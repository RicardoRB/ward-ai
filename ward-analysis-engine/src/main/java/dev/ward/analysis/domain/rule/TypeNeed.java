package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.types.FactKind;
import java.util.Set;

/**
 * The type facts a rule reads and the syntax nodes they are asked for: the rule's {@code TYPES}
 * needs are merged per unit, so the resolver is asked once for every span of a needed node kind.
 *
 * @param nodeKinds the node kinds whose spans the facts are asked for
 * @param facts the kinds of fact the rule reads
 */
public record TypeNeed(Set<String> nodeKinds, Set<FactKind> facts) {

  /**
   * Copies the sets.
   *
   * @throws NullPointerException if a set is missing
   */
  public TypeNeed {
    nodeKinds = Set.copyOf(nodeKinds);
    facts = Set.copyOf(facts);
  }
}
