package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Map;
import java.util.Set;

/**
 * The token kinds a count adds up. A token of a counted kind is counted only when its parent passes
 * the kind's sibling rules:
 *
 * <ul>
 *   <li>{@code requiresSibling}: the parent must also have a direct child of the given kind. A
 *       {@code ?} counts as a conditional only beside its {@code :}, not as a wildcard.
 *   <li>{@code cancelledBySibling}: the parent must not have a direct child of the given kind. A
 *       {@code while} that closes a {@code do} is one decision point, not two.
 * </ul>
 *
 * @param kinds the kinds to count
 * @param requiresSibling the sibling kind each counted kind needs, by kind
 * @param cancelledBySibling the sibling kind that cancels each counted kind, by kind
 */
public record CountedKinds(
    Set<String> kinds,
    Map<String, String> requiresSibling,
    Map<String, String> cancelledBySibling) {

  /**
   * Copies the collections.
   *
   * @throws NullPointerException if a field is missing
   */
  public CountedKinds {
    kinds = Set.copyOf(kinds);
    requiresSibling = Map.copyOf(requiresSibling);
    cancelledBySibling = Map.copyOf(cancelledBySibling);
  }

  /** Counts every node of {@code kinds}, with no sibling rules. */
  public static CountedKinds of(final Set<String> kinds) {
    return new CountedKinds(kinds, Map.of(), Map.of());
  }

  /** Tells whether {@code child} of {@code parent} is counted. */
  public boolean counts(final SyntaxNode parent, final SyntaxNode child) {
    if (!kinds.contains(child.kind())) {
      return false;
    }
    final String needed = requiresSibling.get(child.kind());
    if (needed != null && parent.children().stream().noneMatch(c -> needed.equals(c.kind()))) {
      return false;
    }
    final String cancel = cancelledBySibling.get(child.kind());
    return cancel == null || parent.children().stream().noneMatch(c -> cancel.equals(c.kind()));
  }
}
