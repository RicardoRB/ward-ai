package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The operations on the state of the forward half of the null data-flow. */
final class NullStates {

  private NullStates() {}

  /** Keeps what both states agree on; two values that are not null meet as not null. */
  static Map<Span, Nullity> meet(final Map<Span, Nullity> left, final Map<Span, Nullity> right) {
    final Map<Span, Nullity> met = new HashMap<>();
    left.forEach(
        (variable, value) ->
            agree(value, right.get(variable)).ifPresent(v -> met.put(variable, v)));
    return met;
  }

  private static Optional<Nullity> agree(final Nullity left, final Nullity right) {
    if (right == null) {
      return Optional.empty();
    }
    if (left == right) {
      return Optional.of(left);
    }
    return left.isNonNull() && right.isNonNull() ? Optional.of(Nullity.NONNULL) : Optional.empty();
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
    final Optional<Nullity> value = valueOf(state, event.operand());
    if (value.isPresent()) {
      state.put(event.variable(), value.get());
    } else {
      state.remove(event.variable());
    }
  }

  private static Optional<Nullity> valueOf(
      final Map<Span, Nullity> state, final NullEvent.Operand operand) {
    if (operand.isNull()) {
      return Optional.of(Nullity.NULL);
    }
    if (operand.nonNull()) {
      return Optional.of(Nullity.NONNULL);
    }
    return Optional.ofNullable(operand.variable()).map(state::get);
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
