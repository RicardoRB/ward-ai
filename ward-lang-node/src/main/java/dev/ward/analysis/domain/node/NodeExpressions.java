package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** Classifies the TypeScript and JavaScript expressions that matter for control-flow lowering. */
final class NodeExpressions {

  private static final String SINGLE_EXPRESSION = "singleExpression";
  private static final int BINARY_PARTS = 3;
  private static final int TERNARY_PARTS = 5;
  private static final String CALL = "arguments";
  private static final String OPEN_PAREN = "(";
  private static final String QUESTION = "?";
  private static final Set<String> CALL_TOKENS = Set.of("new", "await", "throw");
  private static final Set<String> LOGICAL = Set.of("&&", "||", "??");
  private static final Set<String> ASSIGNMENTS =
      Set.of("=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=", "**=");

  private NodeExpressions() {}

  /** Reports whether the node is a parenthesized expression, {@code ( expression )}. */
  static boolean isParenthesized(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    return SINGLE_EXPRESSION.equals(node.kind())
        && parts.size() == BINARY_PARTS
        && OPEN_PAREN.equals(NodeFlowSyntax.text(parts.get(0)));
  }

  /**
   * Returns the right-hand side of an assignment, so its operators split the graph, or the
   * expression itself when it is not an assignment.
   */
  static SyntaxNode assignedValue(final SyntaxNode expression) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(expression);
    if (SINGLE_EXPRESSION.equals(expression.kind())
        && parts.size() == BINARY_PARTS
        && ASSIGNMENTS.contains(NodeFlowSyntax.text(parts.get(1)))) {
      return NodeFlowSyntax.unwrap(parts.get(2));
    }
    return expression;
  }

  /** Reports whether the expression is {@code a && b}, {@code a || b} or {@code a ?? b}. */
  static boolean isLogical(final SyntaxNode expression) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(expression);
    return SINGLE_EXPRESSION.equals(expression.kind())
        && parts.size() == BINARY_PARTS
        && LOGICAL.contains(NodeFlowSyntax.text(parts.get(1)));
  }

  /** Reports whether the expression is {@code c ? a : b}. */
  static boolean isTernary(final SyntaxNode expression) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(expression);
    return SINGLE_EXPRESSION.equals(expression.kind())
        && parts.size() == TERNARY_PARTS
        && QUESTION.equals(NodeFlowSyntax.text(parts.get(1)));
  }

  /** Reports whether a subtree can throw: it calls, allocates, awaits or throws. */
  static boolean mayThrow(final SyntaxNode node) {
    if (NodeFlowSyntax.isFunction(node)) {
      return false;
    }
    if (CALL.equals(node.kind())
        || node.children().isEmpty() && CALL_TOKENS.contains(NodeFlowSyntax.text(node))) {
      return true;
    }
    for (final SyntaxNode child : node.children()) {
      if (mayThrow(child)) {
        return true;
      }
    }
    return false;
  }
}
