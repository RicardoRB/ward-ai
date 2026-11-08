package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds where the forward and the backward flow of one type qualifier conflict in a body: at every
 * point a value that promises one thing and is later required to be the other, a value that may
 * lack the qualifier reaching a place that needs it, and two values that are compared and cannot
 * both have it. Each is reported at the place that requires the qualifier.
 */
final class QualReport {

  private final QualSites sites;
  private final QualForward forward;
  private final QualBackward backward;
  private final NullGraph graph;
  private final QualChecks checks;

  QualReport(
      final NullGraph graph,
      final QualSites sites,
      final QualForward forward,
      final QualBackward backward) {
    this.graph = graph;
    this.sites = sites;
    this.forward = forward;
    this.backward = backward;
    this.checks = new QualChecks(sites);
  }

  /** Reports the findings of the body, given its parameters and the state at each block start. */
  List<QualChecks.Found> run(
      final List<VariableAccess> parameters, final Map<Span, QualState.Held> initial) {
    checks.parameters(parameters, initial, backward.before(graph.graph().entry(), 0));
    forward.entries(initial).forEach(this::block);
    return checks.found();
  }

  private void block(final int id, final Map<Span, QualState.Held> entry) {
    final Map<Span, QualState.Held> state = new HashMap<>(entry);
    final List<NullEvent> events = graph.events().get(id);
    for (int index = 0; index < events.size(); index++) {
      checks.conflicts(state, backward.before(id, index));
      final NullEvent event = events.get(index);
      direct(state, event);
      forward.apply(state, event);
      checks.written(event, backward.before(id, index + 1));
    }
    checks.conflicts(state, backward.before(id, events.size()));
  }

  /** Looks at a call, return or store that takes a call or field value directly. */
  private void direct(final Map<Span, QualState.Held> state, final NullEvent event) {
    switch (event.kind()) {
      case EQUALITY -> compared(state, event);
      case CALL -> arguments(event);
      case RETURN -> checks.sink(event.operand(), event.node().span(), sites.returnNeed());
      case STORE ->
          checks.sink(event.operand(), event.node().span(), sites.fieldNeed(event.name()));
      default -> {
        // The other events take no value to a place that requires a qualifier.
      }
    }
  }

  private void arguments(final NullEvent call) {
    for (int index = 0; index < call.args().size(); index++) {
      final NullEvent.Arg arg = call.args().get(index);
      checks.sink(arg.operand(), arg.node().span(), sites.parameterNeed(call, index));
    }
  }

  private void compared(final Map<Span, QualState.Held> state, final NullEvent event) {
    final NullEvent.Arg left = event.args().get(0);
    final NullEvent.Arg right = event.args().get(1);
    if (left.operand().nonNull() || right.operand().nonNull()) {
      return;
    }
    final Optional<QualFlow> first = forward.held(state, left.operand()).map(QualState.Held::flow);
    final Optional<QualFlow> second =
        forward.held(state, right.operand()).map(QualState.Held::flow);
    if (first.isPresent()
        && second.isPresent()
        && !first.get().equals(second.get())
        && QualFlow.conflict(sites.qualifier().strict(), first.get(), second.get())) {
      checks.add(
          QualPattern.COMPARING_VALUES_WITH_INCOMPATIBLE_TYPE_QUALIFIERS, event.node().span());
    }
  }
}
