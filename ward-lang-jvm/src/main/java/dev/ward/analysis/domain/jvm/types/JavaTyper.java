package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Walks the declarations and statements of a Java unit, typing every expression in them. */
final class JavaTyper {
  private static final String EXPRESSION = "expression";
  private static final String IDENTIFIER = "identifier";
  private static final String TYPE_TYPE = "typeType";

  private final Session session;
  private final JavaExpressions expressions;

  JavaTyper(final Session session) {
    this.session = session;
    final JavaCalls calls = new JavaCalls(session);
    this.expressions = new JavaExpressions(session, this, calls);
    calls.connect(this, expressions);
  }

  /** Types the whole unit. */
  void run(final JavaUnitModel model) {
    final Ctx root = new Ctx(model.scope(), java.util.Map.of(), List.of(), Optional.empty());
    model.classes().forEach(declaration -> visitClass(declaration, root));
  }

  private void visitClass(final SourceModel.ClassDecl declaration, final Ctx outer) {
    final JvmClass jvm = declaration.jvm();
    final DeclInfo info =
        new DeclInfo(
            Optional.of(jvm),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            outer.annotations());
    session.declare(declaration.outer(), info);
    session.declare(declaration.node(), info);
    final Ctx inside =
        outer.withScope(declaration.scope(), jvm.typeParameters()).within(jvm.annotations());
    for (final SourceModel.Member member : declaration.members()) {
      member
          .variables()
          .forEach(variable -> session.variable(variable.identifier(), variable.type()));
    }
    for (final SourceModel.Member member : declaration.members()) {
      visitMember(member, jvm, inside);
    }
    declaration.nested().forEach(nested -> visitClass(nested, inside));
  }

  private void visitMember(final SourceModel.Member member, final JvmClass owner, final Ctx ctx) {
    final List<TypeParameter> parameters =
        member.method().map(JvmMethod::typeParameters).orElse(List.of());
    final Ctx inner = ctx.withScope(member.scope(), parameters);
    final DeclInfo info =
        new DeclInfo(
            Optional.empty(),
            member.method(),
            member.field().stream().findFirst(),
            Optional.of(owner),
            ctx.annotations());
    if (member.method().isPresent() || !member.field().isEmpty()) {
      session.declare(member.outer(), info);
      session.declare(member.node(), info);
    }
    member.body().ifPresent(body -> walk(body, inner));
  }

  void walkChildren(final SyntaxNode node, final Ctx ctx) {
    node.children().forEach(child -> walk(child, ctx));
  }

  /** Types the expressions in {@code node} and records the variables it declares. */
  void walk(final SyntaxNode node, final Ctx ctx) {
    switch (node.kind()) {
      case EXPRESSION -> expressions.expression(node, ctx, Optional.empty());
      case "variableDeclarator" -> declarator(node, ctx);
      case "localVariableDeclaration" -> local(node, ctx);
      case "enhancedForControl" -> forEach(node, ctx);
      case "catchClause" -> catchClause(node, ctx);
      case "resource" -> resource(node, ctx);
      case "statement" -> statement(node, ctx);
      case "lambdaExpression" -> expressions.expression(wrap(node), ctx, Optional.empty());
      case "pattern", "switchLabel" -> {
        declarePattern(node, ctx);
        walkChildren(node, ctx);
      }
      default -> walkChildren(node, ctx);
    }
  }

  private static SyntaxNode wrap(final SyntaxNode lambda) {
    return new SyntaxNode(EXPRESSION, lambda.span(), java.util.Map.of(), List.of(lambda));
  }

  private void declarator(final SyntaxNode node, final Ctx ctx) {
    final Optional<JType> declared =
        Syntax.child(node, "variableDeclaratorId")
            .flatMap(id -> Syntax.child(id, IDENTIFIER))
            .flatMap(session::variableType);
    for (final SyntaxNode child : node.children()) {
      if ("variableInitializer".equals(child.kind())) {
        final Optional<SyntaxNode> value = Syntax.child(child, EXPRESSION);
        if (value.isPresent()) {
          expressions.expression(value.get(), ctx, declared);
        } else {
          walkChildren(child, ctx);
        }
      }
    }
  }

  private void local(final SyntaxNode node, final Ctx ctx) {
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
    for (final SyntaxNode declarators : Syntax.children(node, "variableDeclarators")) {
      for (final SyntaxNode declarator : Syntax.children(declarators, "variableDeclarator")) {
        final SyntaxNode id = Syntax.child(declarator, "variableDeclaratorId").orElse(declarator);
        final int dimensions =
            (int) id.children().stream().filter(child -> "[".equals(Syntax.token(child))).count();
        Syntax.child(id, IDENTIFIER)
            .ifPresent(
                name ->
                    session.variable(
                        name, base.map(found -> JavaTypes.arrayOf(found, dimensions))));
        declarator(declarator, ctx);
      }
    }
  }

  private void forEach(final SyntaxNode node, final Ctx ctx) {
    final Optional<SyntaxNode> source = Syntax.child(node, EXPRESSION);
    final Typed iterable =
        source
            .map(found -> expressions.expression(found, ctx, Optional.empty()))
            .orElse(Typed.UNKNOWN);
    final Optional<JType> written =
        Syntax.child(node, TYPE_TYPE).flatMap(found -> JavaTypes.type(found, ctx.scope()));
    final Optional<JType> element = written.or(() -> elementOf(iterable.type()));
    Syntax.child(node, "variableDeclaratorId")
        .flatMap(id -> Syntax.child(id, IDENTIFIER))
        .ifPresent(name -> session.variable(name, element));
  }

  private Optional<JType> elementOf(final Optional<JType> iterable) {
    if (iterable.isEmpty()) {
      return Optional.empty();
    }
    if (iterable.get() instanceof JType.ArrayType array) {
      return Optional.of(array.component());
    }
    if (iterable.get() instanceof JType.ClassType site) {
      return session
          .hierarchy
          .asSuper(site, "java.lang.Iterable")
          .filter(view -> view.arguments().size() == 1)
          .map(view -> view.arguments().get(0))
          .map(
              argument -> argument instanceof JType.Wildcard wildcard ? wildcard.bound() : argument)
          .filter(java.util.Objects::nonNull);
    }
    return Optional.empty();
  }

  private void catchClause(final SyntaxNode node, final Ctx ctx) {
    final List<Optional<JType>> alternatives = new ArrayList<>();
    Syntax.child(node, "catchType").stream()
        .flatMap(types -> Syntax.children(types, "qualifiedName").stream())
        .forEach(
            name ->
                alternatives.add(
                    ctx.scope()
                        .resolve(List.of(Syntax.text(name).split("\\.")))
                        .map(binary -> (JType) JType.ClassType.of(binary))));
    Optional<JType> type = alternatives.isEmpty() ? Optional.empty() : alternatives.get(0);
    for (final Optional<JType> next :
        alternatives.subList(Math.min(1, alternatives.size()), alternatives.size())) {
      type = expressions.unify(type, next);
    }
    final Optional<JType> caught = type;
    Syntax.child(node, IDENTIFIER).ifPresent(name -> session.variable(name, caught));
    Syntax.child(node, "block").ifPresent(block -> walk(block, ctx));
  }

  private void resource(final SyntaxNode node, final Ctx ctx) {
    final Optional<SyntaxNode> value = Syntax.child(node, EXPRESSION);
    final Typed initial =
        value
            .map(found -> expressions.expression(found, ctx, Optional.empty()))
            .orElse(Typed.UNKNOWN);
    final Optional<JType> written =
        Syntax.child(node, "classOrInterfaceType")
            .flatMap(type -> Syntax.child(type, "classType"))
            .flatMap(type -> JavaTypes.classType(type, ctx.scope()));
    final Optional<SyntaxNode> name =
        Syntax.child(node, "variableDeclaratorId")
            .flatMap(id -> Syntax.child(id, IDENTIFIER))
            .or(() -> Syntax.child(node, IDENTIFIER));
    name.ifPresent(found -> session.variable(found, written.or(initial::type)));
  }

  private void statement(final SyntaxNode node, final Ctx ctx) {
    final boolean returns =
        !node.children().isEmpty() && Set2.RETURNS.contains(Syntax.token(node.children().get(0)));
    final Optional<SyntaxNode> value = Syntax.child(node, EXPRESSION);
    if (returns && value.isPresent() && ctx.returns().isPresent()) {
      ctx.returns().get().add(expressions.expression(value.get(), ctx, Optional.empty()).type());
      return;
    }
    walkChildren(node, ctx);
  }

  /** Records the variable a type pattern or case label declares. */
  void declarePattern(final SyntaxNode node, final Ctx ctx) {
    final Optional<SyntaxNode> written = Syntax.child(node, TYPE_TYPE);
    if (written.isEmpty()) {
      return;
    }
    final Optional<JType> type = JavaTypes.type(written.get(), ctx.scope());
    Syntax.child(node, "variableDeclarators").stream()
        .flatMap(list -> Syntax.children(list, "variableDeclarator").stream())
        .flatMap(declarator -> Syntax.child(declarator, "variableDeclaratorId").stream())
        .flatMap(id -> Syntax.child(id, IDENTIFIER).stream())
        .forEach(name -> session.variable(name, type));
    Syntax.child(node, IDENTIFIER).ifPresent(name -> session.variable(name, type));
  }

  /** Walks a switch expression and collects the types of its results. */
  void walkSwitch(final SyntaxNode node, final Ctx ctx, final List<Optional<JType>> results) {
    for (final SyntaxNode child : node.children()) {
      if (EXPRESSION.equals(child.kind())) {
        expressions.expression(child, ctx, Optional.empty());
      } else if ("switchLabeledRule".equals(child.kind())) {
        rule(child, ctx, results);
      }
    }
  }

  private void rule(final SyntaxNode node, final Ctx ctx, final List<Optional<JType>> results) {
    for (final SyntaxNode part : node.children()) {
      if ("switchRuleOutcome".equals(part.kind())) {
        outcome(part, ctx, results);
      } else {
        walk(part, ctx);
      }
    }
  }

  private void outcome(final SyntaxNode node, final Ctx ctx, final List<Optional<JType>> results) {
    for (final SyntaxNode statement : node.children()) {
      final Optional<SyntaxNode> value =
          Syntax.find(statement, "statement").flatMap(found -> Syntax.child(found, EXPRESSION));
      if (value.isPresent() && !Syntax.find(statement, "block").isPresent()) {
        results.add(expressions.expression(value.get(), ctx, Optional.empty()).type());
      } else {
        walk(statement, ctx);
      }
    }
  }

  /** Words that start a statement with a result. */
  private static final class Set2 {
    static final java.util.Set<String> RETURNS = java.util.Set.of("return", "yield");

    private Set2() {}
  }
}
