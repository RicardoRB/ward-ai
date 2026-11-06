package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each scope node (the filter's kinds) that holds more than {@code max} descendants of the
 * counted kinds, not entering {@code skip} subtrees. The user option is {@code max}.
 */
public final class CountCheck implements UnitCheck {

  private final NodeFilter scope;
  private final Set<String> counted;
  private final Set<String> skip;
  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param scope the scopes to measure
   * @param counted the kinds to count
   * @param skip the kinds whose subtrees are not entered; the node itself is still counted
   * @param max the largest allowed count
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public CountCheck(
      final NodeFilter scope,
      final Set<String> counted,
      final Set<String> skip,
      final int max,
      final String message) {
    this.scope = Objects.requireNonNull(scope, "scope");
    this.counted = Set.copyOf(counted);
    this.skip = Set.copyOf(skip);
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return scope.select(root, file).stream()
        .filter(visit -> count(visit.node()) > max)
        .map(
            visit ->
                CheckSupport.at(
                    visit.node().span(),
                    CheckSupport.fill(
                        message, String.valueOf(count(visit.node())), String.valueOf(max))))
        .toList();
  }

  private int count(final SyntaxNode node) {
    int total = 0;
    for (final SyntaxNode child : node.children()) {
      total += counted.contains(child.kind()) ? 1 : 0;
      total += skip.contains(child.kind()) ? 0 : count(child);
    }
    return total;
  }
}
