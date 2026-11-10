package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * A labelled transfer of control to another block of the same graph.
 *
 * @param target the index of the destination block
 * @param kind why control moves there
 */
public record FlowEdge(int target, FlowEdgeKind kind) {

  /**
   * Validates the fields.
   *
   * @throws IllegalArgumentException if the target is negative
   * @throws NullPointerException if the kind is missing
   */
  public FlowEdge {
    if (target < 0) {
      throw new IllegalArgumentException("target must not be negative");
    }
    Objects.requireNonNull(kind, "kind");
  }
}
