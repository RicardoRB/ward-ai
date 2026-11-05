package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the Java parse tree for control-flow lowering. Comment and error nodes are skipped, so the
 * positions of the grammar's parts stay fixed.
 */
final class JavaFlowSyntax {

  private static final int BINARY_PARTS = 3;
  private static final int TERNARY_PARTS = 5;
  private static final int PRIMARY_PARTS = 3;
  private static final String COMMENT = "comment";
  private static final String ERROR = "error";
  private static final String TEXT = "text";
  private static final String EXPRESSION = "expression";
  private static final String PRIMARY = "primary";
  private static final String METHOD_CALL = "methodCall";
  private static final String LAMBDA = "lambdaExpression";
  private static final Set<String> CALL_TOKENS = Set.of("new", "throw");
  private static final Set<String> LOGICAL = Set.of("&&", "||");
  private static final Set<String> ASSIGNMENTS =
      Set.of("=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=");

  private JavaFlowSyntax() {}

  /** Returns the children that are grammar parts, in source order. */
  static List<SyntaxNode> parts(final SyntaxNode node) {
    final List<SyntaxNode> parts = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (!COMMENT.equals(child.kind()) && !ERROR.equals(child.kind())) {
        parts.add(child);
      }
    }
    return parts;
  }

  /** Returns the first of the given parts that has the given kind, or null. */
  static SyntaxNode firstOf(final List<SyntaxNode> parts, final String kind) {
    for (final SyntaxNode part : parts) {
      if (kind.equals(part.kind())) {
        return part;
      }
    }
    return null;
  }

  /** Returns the given parts that have the given kind, in source order. */
  static List<SyntaxNode> allOf(final List<SyntaxNode> parts, final String kind) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode part : parts) {
      if (kind.equals(part.kind())) {
        found.add(part);
      }
    }
    return found;
  }

  /** Returns the source text of a node, the concatenation of its tokens without comments. */
  static String text(final SyntaxNode node) {
    if (COMMENT.equals(node.kind())) {
      return "";
    }
    if (node.children().isEmpty()) {
      return node.fields().getOrDefault(TEXT, "");
    }
    final StringBuilder out = new StringBuilder();
    for (final SyntaxNode child : node.children()) {
      out.append(text(child));
    }
    return out.toString();
  }

  /** Strips the expression wrappers and parentheses around an expression. */
  static SyntaxNode unwrap(final SyntaxNode expression) {
    SyntaxNode current = expression;
    boolean stripped = true;
    while (stripped) {
      stripped = false;
      final List<SyntaxNode> parts = parts(current);
      if (EXPRESSION.equals(current.kind()) && parts.size() == 1) {
        current = parts.get(0);
        stripped = true;
      } else if (PRIMARY.equals(current.kind())
          && parts.size() == PRIMARY_PARTS
          && "(".equals(text(parts.get(0)))) {
        current = parts.get(1);
        stripped = true;
      }
    }
    return current;
  }

  /** Reports whether the expression is {@code a && b} or {@code a || b}. */
  static boolean isLogical(final SyntaxNode expression) {
    final List<SyntaxNode> parts = parts(expression);
    return EXPRESSION.equals(expression.kind())
        && parts.size() == BINARY_PARTS
        && LOGICAL.contains(text(parts.get(1)));
  }

  /**
   * Returns the right-hand side of an assignment, so its operators split the graph, or the
   * expression itself when it is not an assignment.
   */
  static SyntaxNode assignedValue(final SyntaxNode expression) {
    final List<SyntaxNode> parts = parts(expression);
    if (EXPRESSION.equals(expression.kind())
        && parts.size() == BINARY_PARTS
        && ASSIGNMENTS.contains(text(parts.get(1)))) {
      return unwrap(parts.get(2));
    }
    return expression;
  }

  /** Reports whether the expression is {@code c ? a : b}. */
  static boolean isTernary(final SyntaxNode expression) {
    final List<SyntaxNode> parts = parts(expression);
    return EXPRESSION.equals(expression.kind())
        && parts.size() == TERNARY_PARTS
        && "?".equals(text(parts.get(1)));
  }

  /** Reports whether a subtree can throw: it calls a method, allocates, or throws. */
  static boolean mayThrow(final SyntaxNode node) {
    if (LAMBDA.equals(node.kind())) {
      return false;
    }
    if (METHOD_CALL.equals(node.kind())
        || node.children().isEmpty() && CALL_TOKENS.contains(text(node))) {
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
