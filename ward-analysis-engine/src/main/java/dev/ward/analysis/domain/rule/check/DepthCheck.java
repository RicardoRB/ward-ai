package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each node of the filter's kinds that has more than {@code max} ancestors of those same
 * kinds, counting up to the nearest ancestor in {@code reset}. The user option is {@code max}.
 */
public final class DepthCheck implements UnitCheck {

  private final NodeFilter filter;
  private final Set<String> reset;
  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param filter the nesting kinds
   * @param reset the kinds that start a new scope, such as a method
   * @param max the largest allowed depth; the outermost node has depth 0
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public DepthCheck(
      final NodeFilter filter, final Set<String> reset, final int max, final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.reset = Set.copyOf(reset);
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return filter.select(root, file).stream()
        .filter(visit -> depth(visit.ancestors()) > max)
        .map(
            visit ->
                CheckSupport.at(
                    visit.node().span(),
                    CheckSupport.fill(
                        message, String.valueOf(depth(visit.ancestors())), String.valueOf(max))))
        .toList();
  }

  private int depth(final List<SyntaxNode> ancestors) {
    int depth = 0;
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final String kind = ancestors.get(index).kind();
      if (reset.contains(kind)) {
        break;
      }
      depth += filter.isKind(ancestors.get(index)) ? 1 : 0;
    }
    return depth;
  }
}
