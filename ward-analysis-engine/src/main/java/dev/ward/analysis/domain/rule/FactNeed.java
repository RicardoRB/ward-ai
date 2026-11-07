package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.types.FactKind;
import java.util.Set;

/**
 * What one rule needs from the type facts: the syntax node kinds it asks about and the kinds of
 * fact it asks for each. The engine merges the needs of all rules of a language and asks the
 * resolver once per unit.
 *
 * @param nodeKinds the kinds of syntax node whose spans the rule queries
 * @param facts the kinds of fact it reads
 */
public record FactNeed(Set<String> nodeKinds, Set<FactKind> facts) {

  /** The need of a rule that reads no type facts. */
  public static final FactNeed NONE = new FactNeed(Set.of(), Set.of());

  /** Copies the sets. */
  public FactNeed {
    nodeKinds = Set.copyOf(nodeKinds);
    facts = Set.copyOf(facts);
  }
}
