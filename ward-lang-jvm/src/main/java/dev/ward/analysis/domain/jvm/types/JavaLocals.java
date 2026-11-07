package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Records the types of the variables Java statements declare: locals, loop and catch variables. */
final class JavaLocals {
  private static final String EXPRESSION = "expression";
  private static final String IDENTIFIER = "identifier";
  private static final String TYPE_TYPE = "typeType";
  private static final String DECLARATOR_ID = "variableDeclaratorId";
  private static final String DECLARATOR = "variableDeclarator";
  private static final String BRACKET = "[";

  private final Session session;
  private final JavaExpressions expressions;

  JavaLocals(final Session session, final JavaExpressions expressions) {
    this.session = session;
    this.expressions = expressions;
  }

  void declarator(final SyntaxNode node, final Ctx ctx) {
    final Optional<JType> declared =
        Syntax.child(node, DECLARATOR_ID)
            .flatMap(id -> Syntax.child(id, IDENTIFIER))
            .flatMap(session::variableType);
    Syntax.children(node, "variableInitializer")
        .forEach(
            initializer ->
                Syntax.child(initializer, EXPRESSION)
                    .ifPresentOrElse(
                        value -> expressions.expression(value, ctx, declared),
                        () -> initializer.children().forEach(child -> walkOther(child, ctx))));
  }

  private void walkOther(final SyntaxNode child, final Ctx ctx) {
    if (EXPRESSION.equals(child.kind())) {
      expressions.expression(child, ctx, Optional.empty());
    } else {
      child.children().forEach(inner -> walkOther(inner, ctx));
    }
  }

  void local(final SyntaxNode node, final Ctx ctx) {
    final Optional<SyntaxNode> inferred = Syntax.child(node, EXPRESSION);
    if (inferred.isPresent()) {
      final Typed value = expressions.expression(inferred.get(), ctx, Optional.empty());
      Syntax.child(node, IDENTIFIER)
          .ifPresent(
              name -> session.variable(name, value.type().filter(type -> !Types.isNull(type))));
      return;
    }
    final Optional<JType> base =
        Syntax.child(node, TYPE_TYPE).flatMap(written -> JavaTypes.type(written, ctx.scope()));
    Syntax.children(node, "variableDeclarators").stream()
        .flatMap(declarators -> Syntax.children(declarators, DECLARATOR).stream())
        .forEach(declarator -> declare(declarator, base, ctx));
  }

  private void declare(final SyntaxNode declarator, final Optional<JType> base, final Ctx ctx) {
    final SyntaxNode id = Syntax.child(declarator, DECLARATOR_ID).orElse(declarator);
    final int dimensions =
        (int) id.children().stream().filter(child -> BRACKET.equals(Syntax.token(child))).count();
    Syntax.child(id, IDENTIFIER)
        .ifPresent(
            name -> session.variable(name, base.map(found -> JavaTypes.arrayOf(found, dimensions))));
    declarator(declarator, ctx);
  }

  void forEach(final SyntaxNode node, final Ctx ctx) {
    final Typed iterable =
        Syntax.child(node, EXPRESSION)
            .map(found -> expressions.expression(found, ctx, Optional.empty()))
            .orElse(Typed.UNKNOWN);
    final Optional<JType> written =
        Syntax.child(node, TYPE_TYPE).flatMap(found -> JavaTypes.type(found, ctx.scope()));
    final Optional<JType> element = written.or(() -> elementOf(iterable.type()));
    Syntax.child(node, DECLARATOR_ID)
        .flatMap(id -> Syntax.child(id, IDENTIFIER))
        .ifPresent(name -> session.variable(name, element));
  }

  private Optional<JType> elementOf(final Optional<JType> iterable) {
    if (iterable.isPresent() && iterable.get() instanceof JType.ArrayType array) {
      return Optional.of(array.component());
    }
    if (iterable.isPresent() && iterable.get() instanceof JType.ClassType site) {
      return session.hierarchy
          .asSuper(site, "java.lang.Iterable")
          .filter(view -> view.arguments().size() == 1)
          .map(view -> view.arguments().get(0))
          .map(argument -> argument instanceof JType.Wildcard wildcard ? wildcard.bound() : argument)
          .filter(Objects::nonNull);
    }
    return Optional.empty();
  }

  void catchClause(final SyntaxNode node, final Ctx ctx) {
    final List<Optional<JType>> alternatives =
        Syntax.child(node, "catchType").stream()
            .flatMap(types -> Syntax.children(types, "qualifiedName").stream())
            .map(name -> ctx.scope().resolve(List.of(Syntax.text(name).split("\\."))))
            .map(binary -> binary.map(found -> (JType) JType.ClassType.of(found)))
            .toList();
    Optional<JType> type = alternatives.isEmpty() ? Optional.empty() : alternatives.get(0);
    for (final Optional<JType> next : alternatives.stream().skip(1).toList()) {
      type = expressions.unify(type, next);
    }
    final Optional<JType> caught = type;
    Syntax.child(node, IDENTIFIER).ifPresent(name -> session.variable(name, caught));
  }

  void resource(final SyntaxNode node, final Ctx ctx) {
    final Typed initial =
        Syntax.child(node, EXPRESSION)
            .map(found -> expressions.expression(found, ctx, Optional.empty()))
            .orElse(Typed.UNKNOWN);
    final Optional<JType> written =
        Syntax.child(node, "classOrInterfaceType")
            .flatMap(type -> Syntax.child(type, "classType"))
            .flatMap(type -> JavaTypes.classType(type, ctx.scope()));
    Syntax.child(node, DECLARATOR_ID)
        .flatMap(id -> Syntax.child(id, IDENTIFIER))
        .or(() -> Syntax.child(node, IDENTIFIER))
        .ifPresent(found -> session.variable(found, written.or(initial::type)));
  }

  /** Records the variable a type pattern or case label declares. */
  void declarePattern(final SyntaxNode node, final Ctx ctx) {
    final Optional<JType> type =
        Syntax.child(node, TYPE_TYPE).flatMap(written -> JavaTypes.type(written, ctx.scope()));
    if (Syntax.child(node, TYPE_TYPE).isEmpty()) {
      return;
    }
    Syntax.child(node, "variableDeclarators").stream()
        .flatMap(list -> Syntax.children(list, DECLARATOR).stream())
        .flatMap(declarator -> Syntax.child(declarator, DECLARATOR_ID).stream())
        .flatMap(id -> Syntax.child(id, IDENTIFIER).stream())
        .forEach(name -> session.variable(name, type));
    Syntax.child(node, IDENTIFIER).ifPresent(name -> session.variable(name, type));
  }
}
