package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Walks the declarations and statements of a Java unit, typing every expression in them. */
final class JavaTyper {
  private static final String EXPRESSION = "expression";

  private final Session session;
  private final JavaExpressions expressions;
  private final JavaLocals locals;
  private final JavaStatements statements;

  JavaTyper(final Session session) {
    this.session = session;
    final JavaCalls calls = new JavaCalls(session);
    this.expressions = new JavaExpressions(session, this, calls);
    this.locals = new JavaLocals(session, expressions);
    this.statements = new JavaStatements(this, expressions);
    calls.connect(this, expressions);
  }

  /** Types the whole unit. */
  void run(final JavaUnitModel model) {
    final Ctx root = new Ctx(model.scope(), Map.of(), List.of(), Optional.empty());
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
    declaration.members().stream()
        .flatMap(member -> member.variables().stream())
        .forEach(variable -> session.variable(variable.identifier(), variable.type()));
    declaration.members().forEach(member -> visitMember(member, jvm, inside));
    declaration.nested().forEach(nested -> visitClass(nested, inside));
  }

  private void visitMember(final SourceModel.Member member, final JvmClass owner, final Ctx ctx) {
    final List<TypeParameter> parameters =
        member.method().map(JvmMethod::typeParameters).orElse(List.of());
    final Ctx inner = ctx.withScope(member.scope(), parameters);
    if (member.method().isPresent() || !member.field().isEmpty()) {
      final DeclInfo info =
          new DeclInfo(
              Optional.empty(),
              member.method(),
              member.field().stream().findFirst(),
              Optional.of(owner),
              ctx.annotations());
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
      case "lambdaExpression" -> expressions.expression(wrap(node), ctx, Optional.empty());
      case "pattern", "switchLabel" -> {
        locals.declarePattern(node, ctx);
        walkChildren(node, ctx);
      }
      default -> declaration(node, ctx);
    }
  }

  private void declaration(final SyntaxNode node, final Ctx ctx) {
    switch (node.kind()) {
      case "variableDeclarator" -> locals.declarator(node, ctx);
      case "localVariableDeclaration" -> locals.local(node, ctx);
      case "enhancedForControl" -> locals.forEach(node, ctx);
      case "catchClause" -> catchClause(node, ctx);
      case "resource" -> locals.resource(node, ctx);
      case "statement" -> statements.statement(node, ctx);
      default -> walkChildren(node, ctx);
    }
  }

  private void catchClause(final SyntaxNode node, final Ctx ctx) {
    locals.catchClause(node, ctx);
    Syntax.child(node, "block").ifPresent(block -> walk(block, ctx));
  }

  private static SyntaxNode wrap(final SyntaxNode lambda) {
    return new SyntaxNode(EXPRESSION, lambda.span(), Map.of(), List.of(lambda));
  }

  /** Records the variable a type pattern or case label declares. */
  void declarePattern(final SyntaxNode node, final Ctx ctx) {
    locals.declarePattern(node, ctx);
  }

  /** Walks a switch expression and collects the types of its results. */
  void walkSwitch(final SyntaxNode node, final Ctx ctx, final List<Optional<JType>> results) {
    statements.walkSwitch(node, ctx, results);
  }
}
