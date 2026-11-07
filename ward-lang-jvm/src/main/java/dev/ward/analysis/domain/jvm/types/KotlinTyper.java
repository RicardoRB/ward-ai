package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Types the expressions of Kotlin that resolve to declared types: literals, names, property reads,
 * constructor calls and calls of methods whose parameters and results have declared types. What
 * needs full Kotlin inference, such as lambdas, extension functions and flow typing, stays unknown.
 */
final class KotlinTyper {
  private static final String SIMPLE_IDENTIFIER = "simpleIdentifier";
  private static final String EXPRESSION = "expression";
  private static final String VARIABLE = "variableDeclaration";

  private final Session session;
  private final KotlinExpressions expressions;

  KotlinTyper(final Session session) {
    this.session = session;
    this.expressions = new KotlinExpressions(session, this);
  }

  void run(final KotlinUnitModel model) {
    final Ctx root = new Ctx(model.scope(), Map.of(), List.of(), Optional.empty());
    for (final KotlinUnitModel.Declared declared : model.classes()) {
      final DeclInfo info =
          new DeclInfo(
              Optional.of(declared.jvm()),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              List.of());
      session.declare(declared.node(), info);
      final Ctx inside = root.withScope(declared.scope(), declared.jvm().typeParameters());
      Syntax.child(declared.node(), "primaryConstructor")
          .flatMap(constructor -> Syntax.child(constructor, "classParameters"))
          .stream()
          .flatMap(list -> Syntax.children(list, "classParameter").stream())
          .forEach(parameter -> declare(parameter, inside));
      declared.members().stream()
          .filter(member -> "propertyDeclaration".equals(member.kind()))
          .flatMap(member -> Syntax.child(member, VARIABLE).stream())
          .forEach(variable -> declareWritten(variable, inside));
      walk(declared.node(), inside);
    }
  }

  private void declare(final SyntaxNode holder, final Ctx ctx) {
    Syntax.child(holder, SIMPLE_IDENTIFIER)
        .ifPresent(
            name ->
                session.variable(
                    name,
                    Syntax.child(holder, "type")
                        .flatMap(written -> KotlinTypes.type(written, ctx.scope()))));
  }

  private void declareWritten(final SyntaxNode variable, final Ctx ctx) {
    final Optional<JType> written =
        Syntax.child(variable, "type").flatMap(type -> KotlinTypes.type(type, ctx.scope()));
    if (written.isPresent()) {
      Syntax.child(variable, SIMPLE_IDENTIFIER).ifPresent(name -> session.variable(name, written));
    }
  }

  void walk(final SyntaxNode node, final Ctx ctx) {
    switch (node.kind()) {
      case EXPRESSION -> expressions.expr(node, ctx);
      case "propertyDeclaration" -> property(node, ctx);
      case "functionDeclaration" -> function(node, ctx);
      case "forExpression" -> forLoop(node, ctx);
      default -> node.children().forEach(child -> walk(child, ctx));
    }
  }

  private void property(final SyntaxNode node, final Ctx ctx) {
    final Typed initial =
        Syntax.child(node, EXPRESSION)
            .map(found -> expressions.expr(found, ctx))
            .orElse(Typed.UNKNOWN);
    final Optional<SyntaxNode> variable = Syntax.child(node, VARIABLE);
    final Optional<JType> written =
        variable
            .flatMap(found -> Syntax.child(found, "type"))
            .flatMap(type -> KotlinTypes.type(type, ctx.scope()));
    variable
        .flatMap(found -> Syntax.child(found, SIMPLE_IDENTIFIER))
        .ifPresent(
            name ->
                session.variable(
                    name, written.or(() -> initial.type().filter(type -> !Types.isNull(type)))));
  }

  private void function(final SyntaxNode node, final Ctx ctx) {
    Syntax.children(node, "functionValueParameters").stream()
        .flatMap(list -> Syntax.children(list, "functionValueParameter").stream())
        .flatMap(holder -> Syntax.child(holder, "parameter").stream())
        .forEach(parameter -> declare(parameter, ctx));
    Syntax.children(node, "functionBody").forEach(body -> walk(body, ctx));
  }

  private void forLoop(final SyntaxNode node, final Ctx ctx) {
    final Typed iterable =
        Syntax.child(node, EXPRESSION)
            .map(found -> expressions.expr(found, ctx))
            .orElse(Typed.UNKNOWN);
    Syntax.child(node, VARIABLE)
        .flatMap(variable -> Syntax.child(variable, SIMPLE_IDENTIFIER))
        .ifPresent(name -> session.variable(name, element(iterable.type())));
    Syntax.children(node, "controlStructureBody").forEach(body -> walk(body, ctx));
  }

  private Optional<JType> element(final Optional<JType> iterable) {
    if (iterable.isPresent() && iterable.get() instanceof JType.ArrayType array) {
      return Optional.of(array.component());
    }
    if (iterable.isPresent() && iterable.get() instanceof JType.ClassType site) {
      return session
          .hierarchy
          .asSuper(site, "java.lang.Iterable")
          .filter(view -> view.arguments().size() == 1)
          .map(view -> view.arguments().get(0));
    }
    return Optional.empty();
  }
}
