package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a comparison by its operator and operands, read from the tree rather than the text: a
 * {@code not} that wraps a membership or identity test (pycodestyle {@code E713} and {@code E714}),
 * and an identity test against a literal (pyflakes {@code F632}).
 */
public final class ComparisonOperatorCheck implements RuleCheck {

  /** The mistakes the check tells apart, one per rule. */
  public enum Problem {
    /** {@code not x in y} instead of {@code x not in y}. */
    NOT_IN,
    /** {@code not x is y} instead of {@code x is not y}. */
    NOT_IS,
    /** An identity test against a string, bytes or number literal. */
    IS_LITERAL
  }

  private static final Set<String> NODES = Set.of("not_test", "comparison");
  private static final int OPERAND_COUNT = 3;
  private static final int OPERATOR = 1;
  private static final int RIGHT = 2;

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the mistake to report
   * @param message the message of each finding
   */
  public ComparisonOperatorCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, NODES)) {
      if (reported(visit.node())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private boolean reported(final SyntaxNode node) {
    return switch (problem) {
      case NOT_IN -> negatedTest(node, "in");
      case NOT_IS -> negatedTest(node, "is");
      case IS_LITERAL -> isLiteral(node);
    };
  }

  /** A {@code not} whose operand is a single comparison with the given operator. */
  private static boolean negatedTest(final SyntaxNode node, final String operator) {
    if (!"not_test".equals(node.kind()) || node.children().size() != 2) {
      return false;
    }
    final SyntaxNode operand = unwrap(node.children().get(1));
    return "comparison".equals(operand.kind())
        && operand.children().size() == OPERAND_COUNT
        && operator.equals(SyntaxWalk.text(operand.children().get(OPERATOR)));
  }

  /** The node under a chain of single-child wrappers, such as a nested {@code not_test}. */
  private static SyntaxNode unwrap(final SyntaxNode node) {
    SyntaxNode current = node;
    while (!"comparison".equals(current.kind()) && current.children().size() == 1) {
      current = current.children().get(0);
    }
    return current;
  }

  /** An identity test whose right operand is a string, bytes or number literal. */
  private static boolean isLiteral(final SyntaxNode node) {
    if (!"comparison".equals(node.kind()) || node.children().size() != OPERAND_COUNT) {
      return false;
    }
    final String operator = SyntaxWalk.text(node.children().get(OPERATOR));
    final String right = SyntaxWalk.text(node.children().get(RIGHT));
    return ("is".equals(operator) || "isnot".equals(operator)) && literal(right);
  }

  private static boolean literal(final String text) {
    return !text.isEmpty()
        && (text.charAt(0) == '"'
            || text.charAt(0) == '\''
            || Character.isDigit(text.charAt(0))
            || text.startsWith("-") && text.length() > 1 && Character.isDigit(text.charAt(1)));
  }
}
