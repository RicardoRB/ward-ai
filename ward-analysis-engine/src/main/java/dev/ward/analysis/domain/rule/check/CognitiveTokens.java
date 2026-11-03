package dev.ward.analysis.domain.rule.check;

import java.util.Objects;
import java.util.Set;

/**
 * The tokens a cognitive complexity score reads.
 *
 * @param structures the tokens that nest, such as {@code if}, with their sibling rules
 * @param flat the tokens that add one wherever they are, such as {@code &&}
 * @param chaining the tokens that continue a chain, such as {@code else}: they add one, unless the
 *     next sibling is a structure of their own (an {@code else if}, which counts itself)
 */
public record CognitiveTokens(CountedKinds structures, Set<String> flat, Set<String> chaining) {

  /**
   * Copies the sets.
   *
   * @throws NullPointerException if a field is missing
   */
  public CognitiveTokens {
    Objects.requireNonNull(structures, "structures");
    flat = Set.copyOf(flat);
    chaining = Set.copyOf(chaining);
  }
}
