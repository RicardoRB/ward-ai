package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.SpanFacts;
import java.util.List;

/** Facts for the constant and the overridden-method tests. */
final class ExtraFacts {

  private ExtraFacts() {}

  static SpanFacts constant(final Object value) {
    return new SpanFacts(
        null,
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new ConstantValue(value),
        null);
  }

  static SpanFacts overriding(final CallTarget... overridden) {
    return new SpanFacts(
        null, null, null, List.of(), List.of(), List.of(overridden), List.of(), null, null);
  }
}
