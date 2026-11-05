package dev.ward.analysis.domain.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the blocks of a finished body into the graph's blocks. A block that no path from the entry
 * reaches and that holds no node is dropped, and the remaining blocks are numbered in order, so the
 * graph shows only reachable code and the code that is there.
 */
final class ControlFlowCompaction {

  private ControlFlowCompaction() {}

  /**
   * Compacts the blocks.
   *
   * @param nodes the nodes of each block, indexed by the original block id
   * @param edges the outgoing edges of each block, indexed by the original block id
   * @return the kept blocks, renumbered from zero in their original order
   */
  static List<BasicBlock> compact(
      final List<List<SyntaxNode>> nodes, final List<List<FlowEdge>> edges) {
    final boolean[] reached = reachableFromEntry(edges);
    final int[] newId = new int[nodes.size()];
    int next = 0;
    for (int id = 0; id < nodes.size(); id++) {
      final boolean keep =
          id == ControlFlowBuilder.ENTRY
              || id == ControlFlowBuilder.EXIT
              || reached[id]
              || !nodes.get(id).isEmpty();
      if (keep) {
        newId[id] = next;
        next++;
      } else {
        newId[id] = -1;
      }
    }
    final List<BasicBlock> blocks = new ArrayList<>();
    for (int id = 0; id < nodes.size(); id++) {
      if (newId[id] >= 0) {
        blocks.add(new BasicBlock(newId[id], nodes.get(id), renumber(edges.get(id), newId)));
      }
    }
    return blocks;
  }

  private static boolean[] reachableFromEntry(final List<List<FlowEdge>> edges) {
    final boolean[] reached = new boolean[edges.size()];
    final List<Integer> pending = new ArrayList<>();
    pending.add(ControlFlowBuilder.ENTRY);
    while (!pending.isEmpty()) {
      final int id = pending.remove(pending.size() - 1);
      if (!reached[id]) {
        reached[id] = true;
        for (final FlowEdge edge : edges.get(id)) {
          pending.add(edge.target());
        }
      }
    }
    return reached;
  }

  private static List<FlowEdge> renumber(final List<FlowEdge> outgoing, final int... newId) {
    final List<FlowEdge> kept = new ArrayList<>();
    for (final FlowEdge edge : outgoing) {
      if (newId[edge.target()] >= 0) {
        kept.add(new FlowEdge(newId[edge.target()], edge.kind()));
      }
    }
    return kept;
  }
}
