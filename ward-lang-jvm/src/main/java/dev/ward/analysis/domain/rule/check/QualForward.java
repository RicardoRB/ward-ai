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
import java.util.Optional;
import java.util.Set;

/**
 * The forward flow of one type qualifier: for each local variable, what the values written to it
 * promise, with where they come from. A variable without an entry is unknown.
 */
final class QualForward {

  private final NullGraph graph;
  private final QualSites sites;

  QualForward(final NullGraph graph, final QualSites sites) {
    this.graph = graph;
    this.sites = sites;
  }

  /** Returns the state at the start of each block that normal control flow reaches. */
  Map<Integer, Map<Span, QualState.Held>> entries(final Map<Span, QualState.Held> initial) {
    final Map<Integer, Map<Span, QualState.Held>> entry = new HashMap<>();
    entry.put(graph.graph().entry(), initial);
    final Deque<Integer> pending = new ArrayDeque<>(List.of(graph.graph().entry()));
    while (!pending.isEmpty()) {
      final BasicBlock block = graph.graph().blocks().get(pending.poll());
      final Map<Span, QualState.Held> out = copy(entry.get(block.id()));
      graph.events().get(block.id()).forEach(event -> apply(out, event));
      for (final FlowEdge edge : block.successors()) {
        if (edge.kind() != FlowEdgeKind.EXCEPTION && merge(entry, edge.target(), out)) {
          pending.add(edge.target());
        }
      }
    }
    return entry;
  }

  private static Map<Span, QualState.Held> copy(final Map<Span, QualState.Held> state) {
    return new HashMap<>(state);
  }

  private static boolean merge(
      final Map<Integer, Map<Span, QualState.Held>> entry,
      final int target,
      final Map<Span, QualState.Held> sent) {
    final Map<Span, QualState.Held> known = entry.get(target);
    final Map<Span, QualState.Held> merged =
        known == null ? new HashMap<>(sent) : QualState.meetHeld(known, sent);
    final boolean changed = !merged.equals(known);
    if (changed) {
      entry.put(target, merged);
    }
    return changed;
  }

  /** Applies {@code event} to {@code state}. */
  void apply(final Map<Span, QualState.Held> state, final NullEvent event) {
    if (event.kind() == NullEvent.Kind.KILL) {
      state.remove(event.variable());
    } else if (event.kind() == NullEvent.Kind.WRITE) {
      final Optional<QualState.Held> value = held(state, event.operand());
      if (value.isPresent()) {
        state.put(event.variable(), value.get());
      } else {
        state.remove(event.variable());
      }
    }
  }

  /** Returns what the value of {@code operand} promises: a variable's value or a source's. */
  Optional<QualState.Held> held(
      final Map<Span, QualState.Held> state, final NullEvent.Operand operand) {
    if (operand.variable() != null) {
      return Optional.ofNullable(state.get(operand.variable()));
    }
    return sites
        .sourceOf(operand)
        .filter(sites::tracked)
        .map(source -> new QualState.Held(QualFlow.of(source.when()), Set.of(source)));
  }
}
