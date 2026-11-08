package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds where the forward and the backward flow of one type qualifier conflict in a body: at every
 * point a value that promises one thing and is later required to be the other, a value that may
 * lack the qualifier reaching a place that needs it, and two values that are compared and cannot
 * both have it. Each is reported at the place that requires the qualifier.
 */
final class QualReport {

  /**
   * One finding.
   *
   * @param pattern what was found
   * @param at where
   */
  record Found(QualPattern pattern, Span at) {}

  private final QualSites sites;
  private final QualForward forward;
  private final QualBackward backward;
  private final NullGraph graph;
  private final Set<Found> found = new HashSet<>();

  QualReport(
      final NullGraph graph,
      final QualSites sites,
      final QualForward forward,
      final QualBackward backward) {
    this.graph = graph;
    this.sites = sites;
    this.forward = forward;
    this.backward = backward;
  }

  /** Reports the findings of the body, given the state at the start of each block. */
  List<Found> run(final Map<Integer, Map<Span, QualState.Held>> entries) {
    entries.forEach(this::block);
    return List.copyOf(found);
  }

  private void block(final int id, final Map<Span, QualState.Held> entry) {
    final Map<Span, QualState.Held> state = new HashMap<>(entry);
    final List<NullEvent> events = graph.events().get(id);
    for (int index = 0; index < events.size(); index++) {
      conflicts(state, backward.before(id, index));
      final NullEvent event = events.get(index);
      direct(state, event);
      forward.apply(state, event);
      written(state, event, backward.before(id, index + 1));
    }
    conflicts(state, backward.before(id, events.size()));
  }

  /** Reports the variables that promise one thing here and are required to be another later. */
  private void conflicts(
      final Map<Span, QualState.Held> state, final Map<Span, QualState.Need> needs) {
    final boolean strict = sites.qualifier().strict();
    needs.forEach(
        (variable, need) -> {
          final QualState.Held held = state.get(variable);
          if (held != null && QualFlow.conflict(strict, held.flow(), need.flow())) {
            add(pattern(strict, held.flow(), need.flow()), first(need));
          }
        });
  }

  private static QualPattern pattern(
      final boolean strict, final QualFlow flow, final QualFlow needed) {
    if (strict && flow == QualFlow.UNKNOWN) {
      return QualPattern.UNKNOWN_VALUE_USED_WHERE_ALWAYS_STRICTLY_REQUIRED;
    }
    return needed == QualFlow.NEVER
        ? QualPattern.ALWAYS_VALUE_USED_WHERE_NEVER_REQUIRED
        : QualPattern.NEVER_VALUE_USED_WHERE_ALWAYS_REQUIRED;
  }

  private static Span first(final QualState.Need need) {
    return need.sinks().stream()
        .map(QualState.Sink::at)
        .min(Comparator.comparingInt(Span::startOffset))
        .orElseThrow();
  }

  private void add(final QualPattern pattern, final Span at) {
    found.add(new Found(pattern, at));
  }

  /** Looks at a call, return or store that takes a call or field value directly. */
  private void direct(final Map<Span, QualState.Held> state, final NullEvent event) {
    if (event.kind() == NullEvent.Kind.EQUALITY) {
      compared(state, event);
    }
    for (int index = 0;
        index < event.args().size() && event.kind() == NullEvent.Kind.CALL;
        index++) {
      sink(
          event.args().get(index).operand(),
          event.args().get(index).node().span(),
          sites.parameterNeed(event, index));
    }
    if (event.kind() == NullEvent.Kind.RETURN) {
      sink(event.operand(), event.node().span(), sites.returnNeed());
    } else if (event.kind() == NullEvent.Kind.STORE) {
      sink(event.operand(), event.node().span(), sites.fieldNeed(event.name()));
    }
  }

  private void sink(final NullEvent.Operand operand, final Span at, final Optional<QualWhen> when) {
    final Optional<QualFlow> needed = when.map(QualFlow::of).filter(f -> f != QualFlow.UNKNOWN);
    final Optional<QualState.Source> source = sites.sourceOf(operand).filter(sites::tracked);
    if (needed.isPresent() && source.isPresent()) {
      final QualState.Need need =
          new QualState.Need(needed.get(), Set.of(new QualState.Sink(at, when.get())));
      source(source.get(), need);
    }
  }

  /** Looks at the value a write gives a variable when it comes from a call or field. */
  private void written(
      final Map<Span, QualState.Held> state,
      final NullEvent event,
      final Map<Span, QualState.Need> after) {
    if (event.kind() == NullEvent.Kind.WRITE) {
      final Optional<QualState.Source> source =
          sites.sourceOf(event.operand()).filter(sites::tracked);
      final QualState.Need need = after.get(event.variable());
      if (source.isPresent() && need != null) {
        source(source.get(), need);
      }
    }
  }

  /** Reports a source whose value is later required to be what the source cannot promise. */
  void source(final QualState.Source source, final QualState.Need need) {
    final boolean strict = sites.qualifier().strict();
    if (QualFlow.conflict(strict, QualFlow.of(source.when()), need.flow())) {
      add(pattern(strict, QualFlow.of(source.when()), need.flow()), first(need));
    } else if (QualFlow.sourceConflicts(strict, need.flow(), source.when())) {
      add(
          need.flow() == QualFlow.NEVER
              ? QualPattern.MAYBE_SOURCE_VALUE_REACHES_NEVER_SINK
              : QualPattern.MAYBE_SOURCE_VALUE_REACHES_ALWAYS_SINK,
          first(need));
    } else if (source.when() == QualWhen.UNKNOWN
        && source.origin() == QualState.Origin.PARAMETER
        && source.explicit()) {
      add(
          need.flow() == QualFlow.NEVER
              ? QualPattern.EXPLICIT_UNKNOWN_SOURCE_VALUE_REACHES_NEVER_SINK
              : QualPattern.EXPLICIT_UNKNOWN_SOURCE_VALUE_REACHES_ALWAYS_SINK,
          first(need));
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
      add(QualPattern.COMPARING_VALUES_WITH_INCOMPATIBLE_TYPE_QUALIFIERS, event.node().span());
    }
  }

  /** Looks at the parameters of the body as sources, given what is required of them first. */
  void parameters(
      final List<dev.ward.analysis.domain.ir.VariableAccess> parameters,
      final Map<Span, QualState.Held> initial) {
    final Map<Span, QualState.Need> needs = backward.before(graph.graph().entry(), 0);
    for (final dev.ward.analysis.domain.ir.VariableAccess parameter : parameters) {
      final QualState.Held held = initial.get(parameter.declaration());
      final QualState.Need need = needs.get(parameter.declaration());
      if (held != null && need != null) {
        held.sources().forEach(source -> source(source, need));
      }
    }
  }
}
