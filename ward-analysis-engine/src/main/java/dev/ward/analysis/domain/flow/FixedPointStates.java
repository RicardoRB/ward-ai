package dev.ward.analysis.domain.flow;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The entry state of each block of a graph, found by iterating the block transfers to a fixed
 * point.
 */
final class FixedPointStates {

  private FixedPointStates() {}

  /** Iterates the transfer function over the blocks until the entry state of each is stable. */
  static BitSet[] entryStates(
      final ControlFlowGraph graph, final ReachingDefinitions.Writes writes) {
    final List<List<Integer>> predecessors = predecessors(graph);
    final int count = graph.blocks().size();
    final BitSet[] in = new BitSet[count];
    final List<BitSet> out =
        new ArrayList<>(IntStream.range(0, count).mapToObj(id -> new BitSet()).toList());
    boolean changed = true;
    while (changed) {
      changed = false;
      for (final BasicBlock block : graph.blocks()) {
        final int id = block.id();
        in[id] = entryOf(predecessors.get(id), out);
        final BitSet result = transfer(block, in[id], writes);
        if (!result.equals(out.get(id))) {
          out.set(id, result);
          changed = true;
        }
      }
    }
    return in;
  }

  private static BitSet entryOf(final List<Integer> predecessors, final List<BitSet> out) {
    final BitSet entry = new BitSet();
    for (final int pred : predecessors) {
      entry.or(out.get(pred));
    }
    return entry;
  }

  private static BitSet transfer(
      final BasicBlock block, final BitSet entry, final ReachingDefinitions.Writes writes) {
    final BitSet result = (BitSet) entry.clone();
    for (final AccessSite site : ReachingDefinitions.sites(block)) {
      if (ReachingDefinitions.isWrite(site.access())) {
        writes.apply(result, site);
      }
    }
    return result;
  }

  private static List<List<Integer>> predecessors(final ControlFlowGraph graph) {
    final List<List<Integer>> predecessors =
        IntStream.range(0, graph.blocks().size())
            .<List<Integer>>mapToObj(id -> new ArrayList<>())
            .toList();
    for (final BasicBlock block : graph.blocks()) {
      for (final FlowEdge edge : block.successors()) {
        predecessors.get(edge.target()).add(block.id());
      }
    }
    return predecessors;
  }
}
