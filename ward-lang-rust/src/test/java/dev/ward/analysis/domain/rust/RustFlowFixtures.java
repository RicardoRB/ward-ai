package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.port.ControlFlowResult;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lowers Rust source and reads the graphs, for the control-flow tests. */
final class RustFlowFixtures {
  private RustFlowFixtures() {}

  /** The graphs of {@code source}, which must parse. */
  static List<ControlFlowGraph> graphs(final String source) {
    final ControlFlowResult result =
        new RustControlFlow().lower(RustScopeFixtures.unit(source), RustScopeFixtures.tree(source));
    return result.graphs();
  }

  /** The graph named {@code name}. */
  static ControlFlowGraph graph(final String source, final String name) {
    return graphs(source).stream()
        .filter(graph -> graph.body().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no graph " + name + " in " + names(source)));
  }

  /** The names of the graphs of {@code source}, in source order. */
  static List<String> names(final String source) {
    return graphs(source).stream().map(ControlFlowGraph::body).toList();
  }

  /** The number of edges of {@code kind}. */
  static long edges(final ControlFlowGraph graph, final FlowEdgeKind kind) {
    return graph.blocks().stream()
        .flatMap(block -> block.successors().stream())
        .filter(edge -> edge.kind() == kind)
        .count();
  }

  /** Whether {@code block} has an edge to the exit. */
  static boolean reachesExit(final ControlFlowGraph graph, final int block) {
    return graph.blocks().stream()
        .filter(candidate -> candidate.id() == block)
        .flatMap(candidate -> candidate.successors().stream())
        .anyMatch(edge -> edge.target() == graph.exit());
  }

  /** Whether some edge goes back to a block that is not after its source. */
  static boolean hasBackEdge(final ControlFlowGraph graph) {
    return graph.blocks().stream()
        .anyMatch(
            block -> block.successors().stream().anyMatch(edge -> isBack(graph, block, edge))
        );
  }

  private static boolean isBack(
      final ControlFlowGraph graph,
      final BasicBlock block,
      final FlowEdge edge
  ) {
    return edge.target() <= block.id()
        && edge.target() != graph.exit()
        && edge.target() != graph.entry();
  }

  /** The ids of the blocks that a path from the entry reaches. */
  static Set<Integer> reachable(final ControlFlowGraph graph) {
    final Set<Integer> seen = new HashSet<>();
    final ArrayDeque<Integer> pending = new ArrayDeque<>(List.of(graph.entry()));
    while (!pending.isEmpty()) {
      final int id = pending.pop();
      if (seen.add(id)) {
        graph.blocks().stream()
            .filter(block -> block.id() == id)
            .flatMap(block -> block.successors().stream())
            .forEach(edge -> pending.push(edge.target()));
      }
    }
    return seen;
  }

  /** Every variable access of the graph, in block order. */
  static List<VariableAccess> accesses(final ControlFlowGraph graph) {
    return graph.blocks().stream().flatMap(block -> block.accesses().stream()).toList();
  }
}
