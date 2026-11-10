package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The checks of one type qualifier on a body: where a value that promises one thing meets a
 * requirement for another, and which sources cannot satisfy what is required of them. The findings
 * are collected without duplicates, each at the place that requires the qualifier.
 */
final class QualChecks {

  private final QualSites sites;
  private final Set<Found> found = new HashSet<>();

  /**
   * One finding.
   *
   * @param pattern what was found
   * @param at where
   */
  record Found(QualPattern pattern, Span at) {}

  QualChecks(final QualSites sites) {
    this.sites = sites;
  }

  /** Returns what was found. */
  List<Found> found() {
    return List.copyOf(found);
  }

  /** Reports the variables that promise one thing here and are required to be another later. */
  void conflicts(final Map<Span, QualState.Held> state, final Map<Span, QualState.Need> needs) {
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

  /** Adds a finding. */
  void add(final QualPattern pattern, final Span at) {
    found.add(new Found(pattern, at));
  }

  /** Looks at a value that a call or field gives straight to a place that requires a qualifier. */
  void sink(final NullEvent.Operand operand, final Span at, final Optional<QualWhen> when) {
    final Optional<QualFlow> needed = when.map(QualFlow::of).filter(f -> f != QualFlow.UNKNOWN);
    final Optional<QualState.Source> source = sites.sourceOf(operand).filter(sites::tracked);
    if (needed.isPresent() && source.isPresent()) {
      source(
          source.get(),
          new QualState.Need(needed.get(), Set.of(new QualState.Sink(at, when.get()))));
    }
  }

  /** Looks at the value a write gives a variable when it comes from a call or field. */
  void written(final NullEvent event, final Map<Span, QualState.Need> after) {
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
    final QualFlow promised = QualFlow.of(source.when());
    if (QualFlow.conflict(strict, promised, need.flow())) {
      add(pattern(strict, promised, need.flow()), first(need));
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

  /** Looks at the parameters of the body as sources, given what is required of them. */
  void parameters(
      final List<VariableAccess> parameters,
      final Map<Span, QualState.Held> initial,
      final Map<Span, QualState.Need> needs) {
    for (final VariableAccess parameter : parameters) {
      final QualState.Held held = initial.get(parameter.declaration());
      final QualState.Need need = needs.get(parameter.declaration());
      if (held != null && need != null) {
        held.sources().forEach(source -> source(source, need));
      }
    }
  }
}
