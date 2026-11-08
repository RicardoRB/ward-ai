package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.HashMap;
import java.util.Map;

/**
 * A control-flow graph with the nullness events of each of its blocks and the test that ends each
 * branching block.
 *
 * @param graph the graph
 * @param events the events of each block, by block id
 * @param tests the branch test of each block that has one, by block id
 */
record NullGraph(
    ControlFlowGraph graph,
    Map<Integer, java.util.List<NullEvent>> events,
    Map<Integer, NullEvents.Test> tests) {

  NullGraph {
    events = Map.copyOf(events);
    tests = Map.copyOf(tests);
  }

  /** Reads the events and tests of every block of {@code graph}. */
  static NullGraph of(final ControlFlowGraph graph) {
    final Map<Integer, java.util.List<NullEvent>> events = new HashMap<>();
    final Map<Integer, NullEvents.Test> tests = new HashMap<>();
    for (final BasicBlock block : graph.blocks()) {
      events.put(block.id(), NullEvents.of(block));
      NullEvents.test(block).ifPresent(test -> tests.put(block.id(), test));
    }
    return new NullGraph(graph, events, tests);
  }
}
