package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.Objects;

/**
 * What the engine derives from one unit for a rule: its control-flow graphs, scopes and type facts.
 * A view the rule's capability does not read is empty.
 *
 * @param flow the control-flow graphs
 * @param scopes the scopes
 * @param types the type facts; unknown facts yield no findings
 */
public record UnitViews(FlowView flow, ScopeView scopes, UnitTypeFacts types) {

  /**
   * Checks the views.
   *
   * @throws NullPointerException if a view is missing
   */
  public UnitViews {
    Objects.requireNonNull(flow, "flow");
    Objects.requireNonNull(scopes, "scopes");
    Objects.requireNonNull(types, "types");
  }
}
