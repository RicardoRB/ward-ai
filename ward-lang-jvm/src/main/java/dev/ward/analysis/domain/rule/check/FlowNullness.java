package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Intraprocedural nullness analysis of one method body on its control-flow graph. It tracks, per
 * local variable and path, whether the value is null, non-null, possibly null or unknown, from
 * literals, null tests, {@code instanceof}, nullness annotations and dereferences. A variable about
 * which nothing is known never produces a finding.
 */
final class FlowNullness {

  private FlowNullness() {}

  /** Returns the findings in {@code graph}, in source order. */
  static List<FlowNullEvent> analyze(final ControlFlowGraph graph, final FlowNullIndex index) {
    final FlowNullScope quiet = new FlowNullScope(graph, index, null);
    final List<FlowNullState> in = fixpoint(graph, quiet);
    final List<FlowNullEvent> events = new ArrayList<>();
    final FlowNullScope loud = new FlowNullScope(graph, index, events);
    final FlowNullTransfer transfer = new FlowNullTransfer(loud);
    for (final BasicBlock block : graph.blocks()) {
      if (in.get(block.id()) != null) {
        transfer.run(block, in.get(block.id()));
      }
    }
    events.sort((a, b) -> Integer.compare(a.span().startOffset(), b.span().startOffset()));
    return events;
  }

  /** Iterates the transfer functions to a fixed point and returns the state at each block start. */
  private static List<FlowNullState> fixpoint(
      final ControlFlowGraph graph, final FlowNullScope scope) {
    final List<FlowNullState> in = new ArrayList<>();
    graph.blocks().forEach(block -> in.add(null));
    in.set(graph.entry(), entry(graph, scope));
    final FlowNullTransfer transfer = new FlowNullTransfer(scope);
    final Deque<Integer> work = new ArrayDeque<>(List.of(graph.entry()));
    while (!work.isEmpty()) {
      final BasicBlock block = graph.blocks().get(work.removeFirst());
      final FlowNullState out = transfer.run(block, in.get(block.id()));
      for (final FlowEdge edge : block.successors()) {
        final FlowNullState carried = along(block, edge, in.get(block.id()), out, scope);
        final FlowNullState before = in.get(edge.target());
        final FlowNullState after = before == null ? carried : before.join(carried);
        if (!after.equals(before)) {
          in.set(edge.target(), after);
          work.addLast(edge.target());
        }
      }
    }
    return in;
  }

  private static FlowNullState along(
      final BasicBlock block,
      final FlowEdge edge,
      final FlowNullState before,
      final FlowNullState out,
      final FlowNullScope scope) {
    if (edge.kind() == FlowEdgeKind.EXCEPTION) {
      return before.join(out).onException();
    }
    final List<SyntaxNode> nodes = block.nodes();
    if (nodes.isEmpty()) {
      return out;
    }
    return FlowNullRefine.along(nodes.get(nodes.size() - 1), edge.kind(), out, scope);
  }

  /** The state at the start of the body: parameters take their value from their annotations. */
  private static FlowNullState entry(final ControlFlowGraph graph, final FlowNullScope scope) {
    final FlowNullState state = new FlowNullState();
    final Optional<FlowNullIndex.Method> method = scope.method();
    for (final VariableAccess access : graph.blocks().get(graph.entry()).accesses()) {
      if (access.kind() == AccessKind.PARAMETER && method.isPresent()) {
        final int position = method.get().paramNames().indexOf(access.name());
        final FlowNullQual qual =
            position < 0 ? FlowNullQual.NONE : method.get().params().get(position);
        state.put(access.declaration(), initial(qual), false);
      }
    }
    return state;
  }

  private static FlowNullValue initial(final FlowNullQual qual) {
    return switch (qual) {
      case ALWAYS -> FlowNullValue.NON_NULL;
      case MAYBE -> FlowNullValue.NULLABLE;
      case NEVER -> FlowNullValue.NEVER;
      default -> FlowNullValue.UNKNOWN;
    };
  }
}
