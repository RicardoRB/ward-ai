package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.types.FactKind;
import java.util.Set;

/**
 * What one rule needs from the type facts: the syntax node kinds it asks about and the kinds of
 * fact it asks for each.
 *
 * @param nodeKinds the kinds of syntax node whose spans the rule queries
 * @param facts the kinds of fact it reads
 */
public record FactNeed(Set<String> nodeKinds, Set<FactKind> facts) {

  /** Copies the sets. */
  public FactNeed {
    nodeKinds = Set.copyOf(nodeKinds);
    facts = Set.copyOf(facts);
  }
}
