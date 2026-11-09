package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports a boolean operation in an {@code if} or {@code while} condition with a neutral literal
 * operand, such as {@code ready and True} or {@code ready or False}, which reduces to the other
 * operand. An operation with a literal that decides the result is not reported, and nor is one with
 * more than two operands.
 */
public final class SimplifiableConditionCheck implements RuleCheck {

  private static final Set<String> OPERATIONS = Set.of("and_test", "or_test");
  private static final Set<String> CONDITIONS = Set.of("if_stmt", "while_stmt");
  private static final String AND = "and";
  private static final String OR = "or";
  private static final String TRUE = "True";
  private static final String FALSE = "False";
  private static final String IDENTIFIER = "[A-Za-z_]\\w*";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SimplifiableConditionCheck(final String message) {
    this.message = java.util.Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, OPERATIONS)) {
      final boolean condition =
          visit.ancestors().stream().anyMatch(a -> CONDITIONS.contains(a.kind()));
      if (condition && simplifies(visit.node())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** Reports whether the operation has its own operator and two operands, one a neutral literal. */
  private static boolean simplifies(final SyntaxNode operation) {
    final String operator = "and_test".equals(operation.kind()) ? AND : OR;
    final String neutral = AND.equals(operator) ? TRUE : FALSE;
    final List<String> operands = new ArrayList<>();
    boolean hasOperator = false;
    for (final SyntaxNode child : operation.children()) {
      final String text = SyntaxWalk.text(child);
      if (child.children().isEmpty() && operator.equals(text)) {
        hasOperator = true;
      } else {
        operands.add(text);
      }
    }
    return hasOperator
        && operands.size() == 2
        && operands.contains(neutral)
        && operands.stream().anyMatch(text -> !neutral.equals(text) && text.matches(IDENTIFIER));
  }
}
