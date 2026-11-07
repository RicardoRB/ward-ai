package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What is known about each local variable at one point: its value and whether the path to here went
 * through an exception edge. A variable missing from the state is unknown.
 */
final class FlowNullState {

  private static final Fact UNKNOWN = new Fact(FlowNullValue.UNKNOWN, false);

  private final Map<Span, Fact> facts;

  /** What is known about one variable. */
  record Fact(FlowNullValue value, boolean exception) {

    /** Returns the fact where two paths meet. */
    Fact meet(final Fact other) {
      return new Fact(value.join(other.value), exception && other.exception);
    }
  }

  FlowNullState() {
    this(new HashMap<>());
  }

  private FlowNullState(final Map<Span, Fact> facts) {
    this.facts = facts;
  }

  /** Returns what is known about the variable declared at {@code declaration}. */
  Fact get(final Span declaration) {
    return facts.getOrDefault(declaration, UNKNOWN);
  }

  /** Records that the variable declared at {@code declaration} now has {@code value}. */
  void put(final Span declaration, final FlowNullValue value, final boolean exception) {
    facts.put(declaration, new Fact(value, exception));
  }

  /** Returns an independent copy. */
  FlowNullState copy() {
    return new FlowNullState(new HashMap<>(facts));
  }

  /**
   * Returns the state at the end of an exception edge: the facts, marked as on an exception path.
   */
  FlowNullState onException() {
    final FlowNullState out = new FlowNullState();
    facts.forEach((span, fact) -> out.facts.put(span, new Fact(fact.value(), true)));
    return out;
  }

  /**
   * Returns the state where this path meets {@code other}. A variable that only one path knows is
   * kept, because the other path comes from before its declaration.
   */
  FlowNullState join(final FlowNullState other) {
    final FlowNullState out = new FlowNullState(new HashMap<>(other.facts));
    for (final Map.Entry<Span, Fact> entry : facts.entrySet()) {
      final Fact theirs = other.facts.get(entry.getKey());
      out.facts.put(
          entry.getKey(), theirs == null ? entry.getValue() : entry.getValue().meet(theirs));
    }
    return out;
  }

  @Override
  public boolean equals(final Object other) {
    return other instanceof FlowNullState state && facts.equals(state.facts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(facts);
  }
}
