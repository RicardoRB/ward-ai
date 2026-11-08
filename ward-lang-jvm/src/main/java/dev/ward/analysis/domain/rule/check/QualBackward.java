package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The backward flow of one type qualifier: for each local variable, what the code after a point
 * requires of it, always or never having the qualifier, with where it requires it.
 */
final class QualBackward {

  private final NullGraph graph;
  private final QualSites sites;
  private final Map<Integer, Map<Span, QualState.Need>> in = new HashMap<>();
  private final Map<Integer, List<Map<Span, QualState.Need>>> points = new HashMap<>();

  QualBackward(final NullGraph graph, final QualSites sites) {
    this.graph = graph;
    this.sites = sites;
    solve();
  }

  /** The requirements before event {@code index} of {@code block}; the last index is the end. */
  Map<Span, QualState.Need> before(final int block, final int index) {
    return points.get(block).get(index);
  }

  private void solve() {
    final List<BasicBlock> blocks = graph.graph().blocks();
    boolean changed = true;
    while (changed) {
      changed = false;
      for (int index = blocks.size() - 1; index >= 0; index--) {
        final BasicBlock block = blocks.get(index);
        final Map<Span, QualState.Need> start = walk(block);
        if (!start.equals(in.get(block.id()))) {
          in.put(block.id(), start);
          changed = true;
        }
      }
    }
  }

  private Map<Span, QualState.Need> out(final BasicBlock block) {
    Map<Span, QualState.Need> met = Map.of();
    for (final FlowEdge edge : block.successors()) {
      if (edge.kind() != FlowEdgeKind.EXCEPTION) {
        met = QualState.meetNeed(met, in.getOrDefault(edge.target(), Map.of()));
      }
    }
    return met;
  }

  private Map<Span, QualState.Need> walk(final BasicBlock block) {
    final List<NullEvent> events = graph.events().get(block.id());
    final List<Map<Span, QualState.Need>> facts = new java.util.ArrayList<>();
    Map<Span, QualState.Need> state = out(block);
    facts.add(state);
    for (int index = events.size() - 1; index >= 0; index--) {
      state = step(state, events.get(index));
      facts.add(0, state);
    }
    points.put(block.id(), List.copyOf(facts));
    return state;
  }

  private Map<Span, QualState.Need> step(
      final Map<Span, QualState.Need> after, final NullEvent event) {
    final Map<Span, QualState.Need> state = new HashMap<>(after);
    switch (event.kind()) {
      case CALL -> arguments(state, event);
      case RETURN -> require(state, event.operand(), event.node(), sites.returnNeed());
      case STORE -> require(state, event.operand(), event.node(), sites.fieldNeed(event.name()));
      case WRITE -> copyBack(state, event);
      case KILL -> state.remove(event.variable());
      default -> {
        // The other events require nothing.
      }
    }
    return state;
  }

  private void arguments(final Map<Span, QualState.Need> state, final NullEvent call) {
    for (int index = 0; index < call.args().size(); index++) {
      final NullEvent.Arg arg = call.args().get(index);
      require(state, arg.operand(), arg.node(), sites.parameterNeed(call, index));
    }
  }

  /** What the code needed of the variable after a write it needed of the value written. */
  private static void copyBack(final Map<Span, QualState.Need> state, final NullEvent event) {
    final QualState.Need needed = state.remove(event.variable());
    if (needed != null && event.operand().variable() != null) {
      state.merge(event.operand().variable(), needed, QualState::joinNeed);
    }
  }

  private static void require(
      final Map<Span, QualState.Need> state,
      final NullEvent.Operand operand,
      final dev.ward.analysis.domain.ir.SyntaxNode node,
      final Optional<QualWhen> when) {
    final Optional<QualFlow> flow =
        when.map(QualFlow::of).filter(found -> found != QualFlow.UNKNOWN);
    if (flow.isPresent() && operand.variable() != null) {
      final QualState.Need need =
          new QualState.Need(flow.get(), Set.of(new QualState.Sink(node.span(), when.get())));
      state.merge(operand.variable(), need, QualState::joinNeed);
    }
  }
}
