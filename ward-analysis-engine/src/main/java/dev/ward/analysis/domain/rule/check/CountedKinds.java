package dev.ward.analysis.domain.rule.check;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The descendant kinds a count adds up, and an optional sibling kind that cancels a count. A node
 * of a counted kind is not counted when its parent also has a direct child of {@code
 * unlessSibling}: a {@code while} that closes a {@code do} statement is one decision point, not
 * two.
 *
 * @param kinds the kinds to count
 * @param unlessSibling a kind that cancels the count of a counted node beside it, if any
 */
public record CountedKinds(Set<String> kinds, Optional<String> unlessSibling) {

  /**
   * Copies the kinds.
   *
   * @throws NullPointerException if a field is missing
   */
  public CountedKinds {
    kinds = Set.copyOf(kinds);
    Objects.requireNonNull(unlessSibling, "unlessSibling");
  }

  /** Counts every node of {@code kinds}, with no cancelling sibling. */
  public static CountedKinds of(final Set<String> kinds) {
    return new CountedKinds(kinds, Optional.empty());
  }
}
