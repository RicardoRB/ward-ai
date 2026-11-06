package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.List;

/** Handles the statements, parameters and lambdas of Java bodies. */
final class JavaScopeBodies {

  private static final String LAMBDA_LVTIPARAMETER = "lambdaLVTIParameter";

  private static final String LAMBDA_PARAMETERS = "lambdaParameters";

  private static final String CLASS_BODY = "classBody";

  private static final String IDENTIFIER = "identifier";
  private static final String EXPRESSION = "expression";
  private static final String DECLARATOR_ID = "variableDeclaratorId";

  private final JavaScopeWalk walk;

  JavaScopeBodies(final JavaScopeWalk walk) {
    this.walk = walk;
  }

  void enumConstant(final SyntaxNode node) {
    walk.types.declare(JavaScopeWalk.identifierOf(node), BindingKind.CONSTANT, true, false, false);
    node.children().stream().filter(child -> !IDENTIFIER.equals(child.kind())).forEach(walk::visit);
  }

  void anonymousClass(final SyntaxNode node) {
    for (final SyntaxNode part : node.children()) {
      if (CLASS_BODY.equals(part.kind())) {
        final int scope = walk.builder.open(ScopeKind.CLASS, part.span());
        walk.builder.markOpen(scope);
        walk.generic(part);
        walk.builder.close();
      } else {
        walk.visit(part);
      }
    }
  }

  void parameter(final SyntaxNode node) {
    final SyntaxNode id =
        node.children().stream()
            .filter(c -> DECLARATOR_ID.equals(c.kind()))
            .findFirst()
            .orElse(null);
    if (id != null) {
      walk.types.declare(
          JavaScopeWalk.identifierOf(id), BindingKind.PARAMETER, false, false, false);
    }
  }

  void statement(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final boolean scoped =
        parts.stream()
                .anyMatch(
                    part ->
                        "forControl".equals(part.kind())
                            || "resourceSpecification".equals(part.kind()))
            || !parts.isEmpty() && "switch".equals(JavaScopeWalk.text(parts.get(0)));
    if (scoped) {
      walk.scoped(node, ScopeKind.BLOCK);
    } else {
      walk.generic(node);
    }
  }

  void enhancedFor(final SyntaxNode node) {
    node.children().stream().filter(part -> EXPRESSION.equals(part.kind())).forEach(walk::visit);
    final SyntaxNode id = JavaSyntax.child(node, DECLARATOR_ID);
    walk.types.declare(JavaScopeWalk.identifierOf(id), BindingKind.VARIABLE, false, true, true);
  }

  void catchClause(final SyntaxNode node) {
    walk.builder.open(ScopeKind.BLOCK, node.span());
    final SyntaxNode name = JavaScopeWalk.identifierOf(node);
    if (name != null) {
      walk.types.declare(name, BindingKind.PARAMETER, false, false, false);
    }
    node.children().stream().filter(part -> "block".equals(part.kind())).forEach(walk::visit);
    walk.builder.close();
  }

  void resource(final SyntaxNode node) {
    final SyntaxNode id =
        node.children().stream()
            .filter(c -> DECLARATOR_ID.equals(c.kind()))
            .findFirst()
            .orElse(null);
    final SyntaxNode name =
        id == null ? JavaScopeWalk.identifierOf(node) : JavaScopeWalk.identifierOf(id);
    node.children().stream().filter(part -> EXPRESSION.equals(part.kind())).forEach(walk::visit);
    if (name != null) {
      walk.types.declare(name, BindingKind.VARIABLE, false, true, true);
    }
  }

  void lambda(final SyntaxNode node) {
    walk.builder.open(ScopeKind.LAMBDA, node.span());
    for (final SyntaxNode part : node.children()) {
      if (LAMBDA_PARAMETERS.equals(part.kind())) {
        lambdaParameters(part);
      } else {
        walk.visit(part);
      }
    }
    walk.builder.close();
  }

  void lambdaParameters(final SyntaxNode node) {
    for (final SyntaxNode part : node.children()) {
      if (IDENTIFIER.equals(part.kind())) {
        walk.types.declare(part, BindingKind.PARAMETER, false, false, false);
      } else {
        collectLambdaTyped(part);
      }
    }
  }

  void collectLambdaTyped(final SyntaxNode node) {
    if (DECLARATOR_ID.equals(node.kind())) {
      walk.types.declare(
          JavaScopeWalk.identifierOf(node), BindingKind.PARAMETER, false, false, false);
    } else if (LAMBDA_LVTIPARAMETER.equals(node.kind())) {
      walk.types.declare(
          JavaScopeWalk.identifierOf(node), BindingKind.PARAMETER, false, false, false);
    } else {
      node.children().forEach(this::collectLambdaTyped);
    }
  }
}
