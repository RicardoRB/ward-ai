package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The forward half of the null data-flow: for each local variable of a body, whether it holds null,
 * a value that is not null, or a value that the code already dereferenced, when every path to a
 * point agrees. A variable without an entry is unknown, and nothing is reported about it. With
 * {@code exceptional} the analysis also follows the edges that leave a block when a statement
 * throws.
 */
final class NullForward {

  private final NullGraph graph;
  private final boolean exceptional;

  NullForward(final NullGraph graph, final boolean exceptional) {
    this.graph = graph;
    this.exceptional = exceptional;
  }

  /** Returns the state at the start of every block that the analysis reaches. */
  Map<Integer, Map<Span, Nullity>> entries(final Map<Span, Nullity> initial) {
    final Map<Integer, Map<Span, Nullity>> entry = new HashMap<>();
    entry.put(graph.graph().entry(), initial);
    final Deque<Integer> pending = new ArrayDeque<>(List.of(graph.graph().entry()));
    while (!pending.isEmpty()) {
      final BasicBlock block = graph.graph().blocks().get(pending.poll());
      final List<Map<Span, Nullity>> before = samples();
      final Map<Span, Nullity> state = copy(entry.get(block.id()));
      for (final NullEvent event : graph.events().get(block.id())) {
        if (event.kind() == NullEvent.Kind.CALL || event.kind() == NullEvent.Kind.DEREF) {
          before.add(copy(state));
        }
        NullStates.apply(state, event);
      }
      for (final FlowEdge edge : block.successors()) {
        final Map<Span, Nullity> sent = along(block, edge, state, before);
        if (sent != null && merge(entry, edge.target(), sent)) {
          pending.add(edge.target());
        }
      }
    }
    return entry;
  }

  private static List<Map<Span, Nullity>> samples() {
    return new java.util.ArrayList<>();
  }

  private static Map<Span, Nullity> copy(final Map<Span, Nullity> state) {
    return new HashMap<>(state);
  }

  private Map<Span, Nullity> along(
      final BasicBlock block,
      final FlowEdge edge,
      final Map<Span, Nullity> state,
      final List<Map<Span, Nullity>> before) {
    if (edge.kind() == FlowEdgeKind.EXCEPTION) {
      return exceptional ? thrown(state, before) : null;
    }
    return NullStates.refined(state, graph.tests().get(block.id()), edge.kind());
  }

  /**
   * The state when the block throws: what holds before every call and dereference, or at the end of
   * a block that has none, where the edge only enters a handler.
   */
  private static Map<Span, Nullity> thrown(
      final Map<Span, Nullity> state, final List<Map<Span, Nullity>> before) {
    Map<Span, Nullity> met = before.isEmpty() ? state : before.get(0);
    for (final Map<Span, Nullity> each : before) {
      met = NullStates.meet(met, each);
    }
    return met;
  }

  private static boolean merge(
      final Map<Integer, Map<Span, Nullity>> entry,
      final int target,
      final Map<Span, Nullity> sent) {
    final Map<Span, Nullity> known = entry.get(target);
    final Map<Span, Nullity> merged =
        known == null ? new HashMap<>(sent) : NullStates.meet(known, sent);
    final boolean changed = !merged.equals(known);
    if (changed) {
      entry.put(target, merged);
    }
    return changed;
  }
}
