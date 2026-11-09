package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports the condition of an {@code if}, {@code while} or {@code do-while} that chains too many
 * logical operators, as detekt's {@code complex-condition} does: the count is the number of {@code
 * &&} and {@code ||} operators plus one, and the rule reports when it reaches the threshold.
 */
public final class KotlinComplexConditionCheck implements RuleCheck {

  private static final String CONDITION = "expression";
  private static final Set<String> OPERATORS = Set.of("'&&'", "'||'");

  private final Set<String> kinds;
  private final int threshold;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kinds the statement kinds whose condition is checked
   * @param threshold the count at which a condition is reported
   * @param message the message; {@code {value}} is the count and {@code {max}} the threshold
   */
  public KotlinComplexConditionCheck(
      final Set<String> kinds, final int threshold, final String message) {
    this.kinds = Set.copyOf(kinds);
    this.threshold = CheckSupport.nonNegative(threshold, "threshold");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, kinds)) {
      condition(visit.node()).ifPresent(condition -> report(condition, matches));
    }
    return List.copyOf(matches);
  }

  private static Optional<SyntaxNode> condition(final SyntaxNode statement) {
    return statement.children().stream().filter(c -> CONDITION.equals(c.kind())).findFirst();
  }

  private void report(final SyntaxNode condition, final List<RuleMatch> matches) {
    final long operators =
        SyntaxWalk.descendants(condition).stream()
            .filter(node -> OPERATORS.contains(node.kind()))
            .count();
    final long count = operators + 1;
    if (operators > 0 && count >= threshold) {
      matches.add(
          CheckSupport.at(
              condition.span(),
              CheckSupport.fill(message, String.valueOf(count), String.valueOf(threshold))));
    }
  }
}
