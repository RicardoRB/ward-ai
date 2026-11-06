package dev.ward.analysis.domain.rule.check;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How one language spells its modifiers: the node kinds that hold modifier tokens, and for each
 * shared modifier name (such as {@code static}) the token texts that mean it. A name without an
 * entry means the token of that name.
 *
 * @param kinds the kinds whose tokens count as modifiers and annotations
 * @param texts the token texts of each modifier name
 */
public record ModifierTable(Set<String> kinds, Map<String, Set<String>> texts) {

  /** A table with no modifier kinds, for languages and rules that filter on no modifier. */
  public static final ModifierTable NONE = new ModifierTable(Set.of(), Map.of());

  /**
   * Copies the collections.
   *
   * @throws NullPointerException if a field is missing
   */
  public ModifierTable {
    kinds = Set.copyOf(kinds);
    final Map<String, Set<String>> copy = new HashMap<>();
    texts.forEach((name, values) -> copy.put(name, Set.copyOf(values)));
    texts = Map.copyOf(copy);
  }

  /** Tells whether the modifier {@code name} is among the token texts {@code present}. */
  boolean has(final List<String> present, final String name) {
    return texts.getOrDefault(name, Set.of(name)).stream().anyMatch(present::contains);
  }
}
