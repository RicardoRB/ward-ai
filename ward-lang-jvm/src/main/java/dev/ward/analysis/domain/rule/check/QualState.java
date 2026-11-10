package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The values of the forward and backward flow of one type qualifier, and their joins. */
final class QualState {

  /** Where a value gets its qualifier from. */
  enum Origin {
    /** A parameter of the method. */
    PARAMETER,
    /** The value a called method returns. */
    RETURN_VALUE,
    /** The value of a field. */
    FIELD_LOAD
  }

  /**
   * A source of a value.
   *
   * @param origin what kind of source
   * @param when what the declaration says
   * @param explicit whether the declaration says it with an annotation
   */
  record Source(Origin origin, QualWhen when, boolean explicit) {}

  /**
   * A value that flows forward.
   *
   * @param flow what is known
   * @param sources where it comes from
   */
  record Held(QualFlow flow, Set<Source> sources) {
    Held {
      sources = Set.copyOf(sources);
    }
  }

  /**
   * A place that requires a qualifier.
   *
   * @param at where
   * @param when what it requires
   */
  record Sink(Span at, QualWhen when) {}

  /**
   * A requirement that flows backward.
   *
   * @param flow what is required
   * @param sinks where
   */
  record Need(QualFlow flow, Set<Sink> sinks) {
    Need {
      sinks = Set.copyOf(sinks);
    }
  }

  private QualState() {}

  /** Joins two forward states; a variable only one of them knows keeps its value. */
  static Map<Span, Held> meetHeld(final Map<Span, Held> left, final Map<Span, Held> right) {
    final Map<Span, Held> met = new HashMap<>(left);
    right.forEach((variable, held) -> met.merge(variable, held, QualState::joinHeld));
    return met;
  }

  private static Held joinHeld(final Held left, final Held right) {
    return new Held(
        QualFlow.meet(left.flow(), right.flow()), union(left.sources(), right.sources()));
  }

  /** Joins two backward states; a variable only one of them knows keeps its requirement. */
  static Map<Span, Need> meetNeed(final Map<Span, Need> left, final Map<Span, Need> right) {
    final Map<Span, Need> met = new HashMap<>(left);
    right.forEach((variable, need) -> met.merge(variable, need, QualState::joinNeed));
    return met;
  }

  static Need joinNeed(final Need left, final Need right) {
    return new Need(QualFlow.meet(left.flow(), right.flow()), union(left.sinks(), right.sinks()));
  }

  private static <T> Set<T> union(final Set<T> left, final Set<T> right) {
    final Set<T> both = new HashSet<>(left);
    both.addAll(right);
    return both;
  }
}
