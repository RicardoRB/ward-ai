package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.types.ConstantValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The calls of AlwaysThrows that fail on their arguments' shape: map duplicates, Instant math. */
final class AlwaysThrowsAmounts {

  private static final String MAP = "com.google.common.collect.ImmutableMap";
  private static final String BIMAP = "com.google.common.collect.ImmutableBiMap";
  private static final String INSTANT = "java.time.Instant";
  private static final String PERIOD = "java.time.Period";
  private static final Set<String> UNSUPPORTED = Set.of("ofMonths", "ofYears");
  private static final Set<String> MOVES = Set.of("plus", "minus");

  private AlwaysThrowsAmounts() {}

  /** Returns why {@code instant.plus(Period.ofMonths(n))} fails, when it is that call. */
  static Optional<String> instant(final CallSite call) {
    if (!INSTANT.equals(call.target().declaringType().name())
        || !MOVES.contains(call.target().name())
        || call.target().isStatic()) {
      return Optional.empty();
    }
    return call.argumentCall(0)
        .filter(amount -> amount.target().isStatic())
        .filter(amount -> PERIOD.equals(amount.target().declaringType().name()))
        .filter(amount -> UNSUPPORTED.contains(amount.target().name()))
        .map(
            amount ->
                "This call will fail at runtime with a UnsupportedTemporalTypeException: Instant"
                    + " does not support "
                    + amount.target().name());
  }

  /** Returns why {@code ImmutableMap.of(k, v, ...)} fails on a repeated constant key. */
  static Optional<String> duplicates(final CallSite call) {
    final boolean map = call.is(MAP, "of", true);
    final boolean bimap = call.is(BIMAP, "of", true);
    if (!map && !bimap) {
      return Optional.empty();
    }
    final List<ConstantValue> keys = at(call, 0);
    final List<ConstantValue> values = bimap ? at(call, 1) : List.of();
    return repeated(keys)
        .or(() -> repeated(values))
        .map(
            repeat ->
                "This ImmutableMap construction will throw (or have known duplicates overwritten)"
                    + " due to duplicates: "
                    + repeat.value());
  }

  private static List<ConstantValue> at(final CallSite call, final int offset) {
    final List<ConstantValue> found = new ArrayList<>();
    for (int index = offset; index < call.arguments().size(); index += 2) {
      call.constant(index).ifPresent(found::add);
    }
    return found;
  }

  private static Optional<ConstantValue> repeated(final List<ConstantValue> constants) {
    final Set<ConstantValue> seen = new HashSet<>();
    return constants.stream().filter(constant -> !seen.add(constant)).findFirst();
  }
}
