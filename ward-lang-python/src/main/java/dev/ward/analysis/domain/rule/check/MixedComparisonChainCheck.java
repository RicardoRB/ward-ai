package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a chained comparison whose operators come from different groups, like Pylint's {@code
 * bad-chained-comparison}: {@code 0 < x is None} chains a value comparison with an identity test.
 * The groups are the value comparisons, the identity tests and the membership tests.
 */
public final class MixedComparisonChainCheck implements RuleCheck {

  private static final Set<String> COMPARISONS = Set.of("comparison");
  private static final String OPERATOR = "comp_op";
  private static final Set<String> VALUE = Set.of("<", "<=", ">", ">=", "!=", "==");
  private static final Set<String> IDENTITY = Set.of("is", "isnot");
  private static final Set<String> MEMBERSHIP = Set.of("in", "notin");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public MixedComparisonChainCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, COMPARISONS)) {
      final List<String> operators = operators(visit.node());
      if (operators.size() > 1 && mixesGroups(operators)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /**
   * The operators of a comparison, in source order, with {@code is not} and {@code not in} joined.
   */
  static List<String> operators(final SyntaxNode comparison) {
    final List<String> operators = new ArrayList<>();
    for (final SyntaxNode child : comparison.children()) {
      if (OPERATOR.equals(child.kind())) {
        operators.add(SyntaxWalk.text(child));
      }
    }
    return operators;
  }

  /** Whether the first operator's group does not hold every operator. */
  static boolean mixesGroups(final List<String> operators) {
    final String first = operators.get(0);
    final Set<String> group =
        VALUE.contains(first) ? VALUE : IDENTITY.contains(first) ? IDENTITY : MEMBERSHIP;
    return !operators.stream().allMatch(group::contains);
  }
}
