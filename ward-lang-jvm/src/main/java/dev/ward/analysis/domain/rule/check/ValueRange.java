package dev.ward.analysis.domain.rule.check;

import java.util.Optional;

/**
 * The integer values a variable can have at a point of a method, known from the comparisons that
 * must have held to get there. A range with {@code low > high} holds no value: the point cannot be
 * reached.
 */
record ValueRange(long low, long high) {

  /** Every {@code long} value. */
  static final ValueRange ANY = new ValueRange(Long.MIN_VALUE, Long.MAX_VALUE);

  private static final ValueRange NONE = new ValueRange(1, 0);

  /** Returns the range after a comparison {@code variable op constant} held. */
  ValueRange holding(final String op, final long constant) {
    return switch (op) {
      case "'>'" -> bounded(Math.max(low, constant + 1), high);
      case "'>='" -> bounded(Math.max(low, constant), high);
      case "'<'" -> bounded(low, Math.min(high, constant - 1));
      case "'<='" -> bounded(low, Math.min(high, constant));
      case "'=='" -> bounded(Math.max(low, constant), Math.min(high, constant));
      case "'!='" -> low == constant && high == constant ? NONE : this;
      default -> this;
    };
  }

  /**
   * Returns whether {@code variable op constant} is true for every value, false for every value.
   */
  Optional<Boolean> decides(final String op, final long constant) {
    final boolean canHold = !holding(op, constant).isEmpty();
    final boolean canFail = !holding(negate(op), constant).isEmpty();
    if (isEmpty() || canHold == canFail) {
      return Optional.empty();
    }
    return Optional.of(canHold);
  }

  /** Returns the operator that holds exactly when {@code op} does not. */
  static String negate(final String op) {
    return switch (op) {
      case "'>'" -> "'<='";
      case "'>='" -> "'<'";
      case "'<'" -> "'>='";
      case "'<='" -> "'>'";
      case "'=='" -> "'!='";
      default -> "'=='";
    };
  }

  /** Returns the operator that gives the same answer with the operands swapped. */
  static String mirror(final String op) {
    return switch (op) {
      case "'>'" -> "'<'";
      case "'>='" -> "'<='";
      case "'<'" -> "'>'";
      case "'<='" -> "'>='";
      default -> op;
    };
  }

  private boolean isEmpty() {
    return low > high;
  }

  private static ValueRange bounded(final long low, final long high) {
    return low > high ? NONE : new ValueRange(low, high);
  }
}
