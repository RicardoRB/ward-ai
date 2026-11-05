package dev.ward.analysis.domain.ir;

import java.util.List;
import java.util.Objects;

/**
 * A straight-line run of statements and condition expressions with its outgoing edges.
 *
 * @param id the index of the block in its graph
 * @param nodes the statement and condition syntax nodes in evaluation order
 * @param successors the outgoing edges, in the order they were added
 * @param accesses the variable accesses of the nodes, in evaluation order
 */
public record BasicBlock(
    int id, List<SyntaxNode> nodes, List<FlowEdge> successors, List<VariableAccess> accesses) {

  /**
   * Creates a block without variable accesses.
   *
   * @param id the index of the block in its graph
   * @param nodes the statement and condition syntax nodes in evaluation order
   * @param successors the outgoing edges, in the order they were added
   */
  public BasicBlock(final int id, final List<SyntaxNode> nodes, final List<FlowEdge> successors) {
    this(id, nodes, successors, List.of());
  }

  /**
   * Copies the lists.
   *
   * @throws IllegalArgumentException if the id is negative
   * @throws NullPointerException if a list is missing or holds a null element
   */
  public BasicBlock {
    if (id < 0) {
      throw new IllegalArgumentException("id must not be negative");
    }
    nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
    successors = List.copyOf(Objects.requireNonNull(successors, "successors"));
    accesses = List.copyOf(Objects.requireNonNull(accesses, "accesses"));
  }
}
