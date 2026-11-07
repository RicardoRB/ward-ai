package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/**
 * Where a call must, and must not, sit. A selector is a node kind, or {@code parent>child} as in
 * {@link NodeFilter}, so {@code statement>'while'} names a {@code while} loop.
 *
 * @param inside selectors of which some ancestor must match, if not empty
 * @param notInside selectors of which no ancestor may match
 */
public record CallMatchContext(Set<String> inside, Set<String> notInside) {

  /** No limit on where the call sits. */
  public static final CallMatchContext ANYWHERE = new CallMatchContext(Set.of(), Set.of());

  /** Copies the sets. */
  public CallMatchContext {
    inside = Set.copyOf(inside);
    notInside = Set.copyOf(notInside);
  }

  boolean admits(final List<SyntaxNode> ancestors) {
    return (inside.isEmpty() || ancestors.stream().anyMatch(a -> matches(inside, a)))
        && ancestors.stream().noneMatch(a -> matches(notInside, a));
  }

  private static boolean matches(final Set<String> selectors, final SyntaxNode node) {
    return selectors.stream().anyMatch(selector -> NodeFilter.selects(selector, node));
  }
}
