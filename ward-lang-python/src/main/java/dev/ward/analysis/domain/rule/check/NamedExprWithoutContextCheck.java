package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an expression statement that is only an assignment expression, like {@code (n := 1)}: the
 * value is bound and then thrown away, so the walrus needs no context and should be a plain
 * assignment. Parentheses around the walrus are allowed; a walrus used inside a larger expression,
 * such as {@code (n := 1).real}, is not reported.
 */
public final class NamedExprWithoutContextCheck implements RuleCheck {

  private static final Set<String> STATEMENTS = Set.of("expr_stmt");
  private static final String NAMED_EXPRESSION = "namedexpr_test";
  private static final int ASSIGNMENT_EXPRESSION_PARTS = 3;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NamedExprWithoutContextCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, STATEMENTS)) {
      final SyntaxNode statement = visit.node();
      if (statement.children().size() == 1 && bareAssignmentExpression(statement).isPresent()) {
        found.add(new RuleMatch(statement.span(), message));
      }
    }
    return List.copyOf(found);
  }

  /**
   * Follows the single operand of each wrapper and parenthesis down to a walrus, if there is one.
   */
  private static Optional<SyntaxNode> bareAssignmentExpression(final SyntaxNode node) {
    SyntaxNode current = node;
    while (!NAMED_EXPRESSION.equals(current.kind())) {
      final List<SyntaxNode> operands = operands(current);
      if (operands.size() != 1) {
        return Optional.empty();
      }
      current = operands.get(0);
    }
    return current.children().size() == ASSIGNMENT_EXPRESSION_PARTS
        ? Optional.of(current)
        : Optional.empty();
  }

  /** The child rule nodes; terminals such as parentheses and operators have no children. */
  private static List<SyntaxNode> operands(final SyntaxNode node) {
    return node.children().stream().filter(child -> !child.children().isEmpty()).toList();
  }
}
