package dev.ward.analysis.domain.rule.check;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Which declarations a check looks at: the node kinds, the kinds that hold modifiers, and the
 * modifiers a declaration must have or must not have.
 *
 * @param kinds the declaration kinds to check
 * @param modifierKinds the kinds whose tokens count as modifiers and annotations
 * @param required a modifier the declaration must have, if any
 * @param excluded a modifier the declaration must not have, if any
 */
public record DeclarationFilter(
    Set<String> kinds,
    Set<String> modifierKinds,
    Optional<String> required,
    Optional<String> excluded) {

  /**
   * Copies the sets.
   *
   * @throws NullPointerException if a field is missing
   */
  public DeclarationFilter {
    kinds = Set.copyOf(kinds);
    modifierKinds = Set.copyOf(modifierKinds);
    Objects.requireNonNull(required, "required");
    Objects.requireNonNull(excluded, "excluded");
  }
}
