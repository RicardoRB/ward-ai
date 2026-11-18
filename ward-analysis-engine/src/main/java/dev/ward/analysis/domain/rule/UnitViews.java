package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.Objects;

/**
 * What the engine derives from one unit for a rule: its control-flow graphs, scopes and type facts and the nodes some rules share.
 * A view the rule's capability does not read is empty.
 *
 * @param flow the control-flow graphs
 * @param scopes the scopes
 * @param types the type facts; unknown facts yield no findings
 * @param nodes the nodes of the kinds that rules share one walk for
 */
public record UnitViews(FlowView flow, ScopeView scopes, UnitTypeFacts types, NodeIndex nodes) {
  /**
   * Checks the views.
   *
   * @throws NullPointerException if a view is missing
   */
  public UnitViews {
    Objects.requireNonNull(flow, "flow");
    Objects.requireNonNull(scopes, "scopes");
    Objects.requireNonNull(types, "types");
    Objects.requireNonNull(nodes, "nodes");
  }
}
