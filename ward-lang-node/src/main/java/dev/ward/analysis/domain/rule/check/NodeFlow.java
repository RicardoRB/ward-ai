package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the control-flow graph of one TypeScript or JavaScript body. A body is found by the span of
 * its function node. Exception edges are not followed: ESLint's rules judge the paths that return.
 */
final class NodeFlow {

  private static final Set<String> JUMPS = Set.of("returnStatement", "throwStatement");

  private NodeFlow() {}

  /** The graph of the body of {@code owner}, when it could be lowered. */
  static Optional<ControlFlowGraph> graphOf(final FlowView flow, final SyntaxNode owner) {
    return flow.graphs().stream().filter(graph -> graph.span().equals(owner.span())).findFirst();
  }

  /** Tells whether the end of the body can be reached without a return or a throw. */
  static boolean fallsOffEnd(final ControlFlowGraph graph) {
    for (final BasicBlock block : reachable(graph)) {
      if (!endsInJump(block) && leadsTo(block, graph.exit())) {
        return true;
      }
    }
    return false;
  }

  /** Tells whether {@code block} has a normal edge to {@code target}. */
  static boolean leadsTo(final BasicBlock block, final int target) {
    return block.successors().stream()
        .anyMatch(edge -> edge.target() == target && edge.kind() != FlowEdgeKind.EXCEPTION);
  }

  /** Tells whether the last statement of {@code block} returns or throws. */
  static boolean endsInJump(final BasicBlock block) {
    if (block.nodes().isEmpty()) {
      return false;
    }
    final SyntaxNode last = block.nodes().get(block.nodes().size() - 1);
    return JUMPS.contains(last.kind());
  }

  /** The blocks reachable from the entry without exception edges, in discovery order. */
  static List<BasicBlock> reachable(final ControlFlowGraph graph) {
    final List<BasicBlock> found = new ArrayList<>();
    final Set<Integer> seen = new HashSet<>();
    final Deque<Integer> work = new ArrayDeque<>();
    work.add(graph.entry());
    seen.add(graph.entry());
    while (!work.isEmpty()) {
      final BasicBlock block = graph.blocks().get(work.poll());
      if (block.id() == graph.exit()) {
        continue;
      }
      found.add(block);
      for (final FlowEdge edge : block.successors()) {
        if (edge.kind() != FlowEdgeKind.EXCEPTION && seen.add(edge.target())) {
          work.add(edge.target());
        }
      }
    }
    return found;
  }
}
