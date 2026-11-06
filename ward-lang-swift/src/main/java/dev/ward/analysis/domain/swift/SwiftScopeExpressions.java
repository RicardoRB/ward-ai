package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.Set;

/** Handles the expressions of Swift: assignments, member access and names. */
final class SwiftScopeExpressions {

  private static final Set<String> COMPOUND =
      Set.of("+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", "&&=", "||=", "??=");
  private static final String IDENTIFIER = "declaration_identifier";
  private static final String PRIMARY = "primary_expression";

  private final SwiftScopeWalk walk;

  SwiftScopeExpressions(final SwiftScopeWalk walk) {
    this.walk = walk;
  }

  /** An {@code if let} binds in the condition and the first block, not in the {@code else}. */
  void ifStatement(final SyntaxNode node) {
    walk.builder.open(ScopeKind.BLOCK, node.span());
    node.children().stream()
        .filter(child -> !"else_clause".equals(child.kind()))
        .forEach(walk::visit);
    walk.builder.close();
    node.children().stream()
        .filter(child -> "else_clause".equals(child.kind()))
        .forEach(walk::visit);
  }

  void expression(final SyntaxNode node) {
    final SyntaxNode target = SwiftScopeWalk.child(node, "prefix_expression");
    final SyntaxNode operators = SwiftScopeWalk.child(node, "binary_expressions");
    if (target != null && operators != null && assignsTo(operators)) {
      final boolean compound = !"=".equals(assignment(operators));
      target(target, compound);
      node.children().stream().filter(c -> !c.equals(target)).forEach(walk::visit);
    } else {
      walk.generic(node);
    }
  }

  static String assignment(final SyntaxNode operators) {
    final SyntaxNode first = SwiftScopeWalk.child(operators, "binary_expression");
    if (first == null || first.children().isEmpty()) {
      return "";
    }
    final SyntaxNode operator = first.children().get(0);
    return "assignment_operator".equals(operator.kind()) ? "=" : SwiftScopeWalk.text(operator);
  }

  static boolean assignsTo(final SyntaxNode operators) {
    final String operator = assignment(operators);
    return "=".equals(operator) || COMPOUND.contains(operator);
  }

  /** The name an expression is made of alone, or null. */
  static SyntaxNode plainName(final SyntaxNode node) {
    SyntaxNode current = node;
    while (("prefix_expression".equals(current.kind())
            || "postfix_expression".equals(current.kind())
            || PRIMARY.equals(current.kind()))
        && current.children().size() == 1) {
      current = current.children().get(0);
    }
    return IDENTIFIER.equals(current.kind()) ? current : null;
  }

  void target(final SyntaxNode expression, final boolean compound) {
    final SyntaxNode name = plainName(expression);
    if (name == null) {
      walk.visit(expression);
      return;
    }
    if (compound) {
      walk.builder.reference(SwiftScopeWalk.text(name), name.span(), ReferenceAccess.READ);
    }
    walk.builder.reference(SwiftScopeWalk.text(name), name.span(), ReferenceAccess.WRITE);
  }

  void primary(final SyntaxNode node) {
    final SyntaxNode name =
        node.children().size() == 1 ? SwiftScopeWalk.child(node, IDENTIFIER) : null;
    if (name == null) {
      walk.generic(node);
    } else {
      walk.builder.reference(SwiftScopeWalk.text(name), name.span(), ReferenceAccess.READ);
    }
  }

  /** In {@code a.b} the name after the dot is a member, not a reference. */
  void postfix(final SyntaxNode node) {
    node.children().stream().filter(c -> !IDENTIFIER.equals(c.kind())).forEach(walk::visit);
  }
}
