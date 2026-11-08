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
      final List<Map<Span, Nullity>> before = new java.util.ArrayList<>();
      final Map<Span, Nullity> state = new HashMap<>(entry.get(block.id()));
      for (final NullEvent event : graph.events().get(block.id())) {
        if (event.kind() == NullEvent.Kind.CALL || event.kind() == NullEvent.Kind.DEREF) {
          before.add(new HashMap<>(state));
        }
        apply(state, event);
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

  private Map<Span, Nullity> along(
      final BasicBlock block,
      final FlowEdge edge,
      final Map<Span, Nullity> state,
      final List<Map<Span, Nullity>> before) {
    if (edge.kind() == FlowEdgeKind.EXCEPTION) {
      return exceptional ? thrown(state, before) : null;
    }
    return refined(state, graph.tests().get(block.id()), edge.kind());
  }

  /**
   * The state when the block throws: what holds before every call and dereference, or at the end of
   * a block that has none, where the edge only enters a handler.
   */
  private static Map<Span, Nullity> thrown(
      final Map<Span, Nullity> state, final List<Map<Span, Nullity>> before) {
    Map<Span, Nullity> met = before.isEmpty() ? state : before.get(0);
    for (final Map<Span, Nullity> each : before) {
      met = meet(met, each);
    }
    return met;
  }

  private static boolean merge(
      final Map<Integer, Map<Span, Nullity>> entry,
      final int target,
      final Map<Span, Nullity> sent) {
    final Map<Span, Nullity> known = entry.get(target);
    final Map<Span, Nullity> merged = known == null ? new HashMap<>(sent) : meet(known, sent);
    final boolean changed = !merged.equals(known);
    if (changed) {
      entry.put(target, merged);
    }
    return changed;
  }

  /** Keeps what both states agree on; two values that are not null meet as not null. */
  static Map<Span, Nullity> meet(final Map<Span, Nullity> left, final Map<Span, Nullity> right) {
    final Map<Span, Nullity> met = new HashMap<>();
    left.forEach(
        (variable, value) ->
            agree(value, right.get(variable)).ifPresent(v -> met.put(variable, v)));
    return met;
  }

  private static java.util.Optional<Nullity> agree(final Nullity left, final Nullity right) {
    if (right == null) {
      return java.util.Optional.empty();
    }
    if (left == right) {
      return java.util.Optional.of(left);
    }
    return left.isNonNull() && right.isNonNull()
        ? java.util.Optional.of(Nullity.NONNULL)
        : java.util.Optional.empty();
  }

  /** Applies {@code event} to {@code state}. */
  static void apply(final Map<Span, Nullity> state, final NullEvent event) {
    switch (event.kind()) {
      case WRITE -> write(state, event);
      case KILL -> state.remove(event.variable());
      case DEREF -> state.put(event.variable(), Nullity.DEREFED);
      default -> {
        // The other events leave the variables as they are.
      }
    }
  }

  private static void write(final Map<Span, Nullity> state, final NullEvent event) {
    final NullEvent.Operand operand = event.operand();
    final Nullity value;
    if (operand.isNull()) {
      value = Nullity.NULL;
    } else if (operand.nonNull()) {
      value = Nullity.NONNULL;
    } else {
      value = operand.variable() == null ? null : state.get(operand.variable());
    }
    if (value == null) {
      state.remove(event.variable());
    } else {
      state.put(event.variable(), value);
    }
  }

  /** Returns the state on the branch of kind {@code kind} after the test that ends the block. */
  static Map<Span, Nullity> refined(
      final Map<Span, Nullity> state, final NullEvents.Test test, final FlowEdgeKind kind) {
    if (test == null || kind != FlowEdgeKind.TRUE && kind != FlowEdgeKind.FALSE) {
      return state;
    }
    final Nullity holds = kind == FlowEdgeKind.TRUE ? test.onTrue() : test.onFalse();
    final Nullity now = state.get(test.variable());
    if (holds == null || holds == Nullity.NULL && now != null && now.isNonNull()) {
      return state;
    }
    final Map<Span, Nullity> sent = new HashMap<>(state);
    sent.put(test.variable(), holds);
    return sent;
  }
}
