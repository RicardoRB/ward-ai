package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/**
 * Reads the assignments of a syntax tree. An assignment is an {@code expression} whose children are
 * the target expression, an assignment operator token and the value expression.
 */
final class Assignments {

  /** The assignment operator tokens, quoted as the syntax tree names them. */
  static final Set<String> OPERATORS =
      Set.of(
          "'='", "'+='", "'-='", "'*='", "'/='", "'%='", "'&='", "'|='", "'^='", "'<<='", "'>>='",
          "'>>>='");

  private static final String EXPRESSION = "expression";

  private Assignments() {}

  /** Returns the assignments in {@code root}, in order, as expression nodes. */
  static List<SyntaxNode> in(final SyntaxNode root) {
    return SyntaxWalk.descendants(root).stream().filter(Assignments::isAssignment).toList();
  }

  /** Returns whether a node is an assignment expression. */
  static boolean isAssignment(final SyntaxNode node) {
    return EXPRESSION.equals(node.kind())
        && node.children().size() == 3
        && OPERATORS.contains(node.children().get(1).kind());
  }

  /** Returns the text of an assignment's target. */
  static String target(final SyntaxNode assignment) {
    return SyntaxWalk.text(assignment.children().get(0));
  }

  /** Returns the text of an assignment's value. */
  static String value(final SyntaxNode assignment) {
    return SyntaxWalk.text(assignment.children().get(2));
  }
}
