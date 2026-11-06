package dev.ward.analysis.domain.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UnitTypeFactsTest {
  private static final Span CALL = new Span(3, 5, 3, 20, 30, 45);
  private static final Span OTHER = new Span(4, 1, 4, 2, 50, 51);
  private static final TypeRef STRING = TypeRef.of("java.lang.String");

  @Test
  void looksUpAnExactSpanBeforeTheStartPosition() {
    final SpanFacts exact = withType(STRING);
    final SpanFacts start = withType(TypeRef.of("other"));
    final UnitTypeFacts facts =
        new UnitTypeFacts(
            Map.of(UnitTypeFacts.key(CALL), exact, UnitTypeFacts.startKey(CALL), start));

    assertThat(facts.typeOf(CALL)).contains(STRING);
  }

  @Test
  void aFactNamedByStartPositionAnswersAnySpanStartingThere() {
    final UnitTypeFacts facts = new UnitTypeFacts(Map.of("3:5", withType(STRING)));

    assertThat(facts.typeOf(CALL)).contains(STRING);
    assertThat(facts.typeOf(OTHER)).isEmpty();
  }

  @Test
  void anUnansweredSpanIsUnknownInEveryQuery() {
    final UnitTypeFacts facts = UnitTypeFacts.none();

    assertThat(facts.typeOf(CALL)).isEmpty();
    assertThat(facts.shapeOf(CALL)).isEmpty();
    assertThat(facts.callTarget(CALL)).isEmpty();
    assertThat(facts.at(CALL).annotations()).isEmpty();
    assertThat(facts.nullness(CALL)).isEqualTo(Nullness.UNKNOWN);
    assertThat(facts.at(CALL).overrides()).isEmpty();
    assertThat(facts.at(CALL).thrown()).isEmpty();
    assertThat(facts.constant(CALL)).isEmpty();
  }

  @Test
  void nullnessCombinesTheAnnotationsWithTheirScopes() {
    final SpanFacts parameter =
        new SpanFacts(
            null,
            null,
            null,
            List.of(),
            List.of("org.jspecify.annotations.NullMarked"),
            List.of(),
            List.of(),
            null);

    assertThat(new UnitTypeFacts(Map.of("3:5", parameter)).nullness(CALL))
        .isEqualTo(Nullness.NON_NULL);
  }

  @Test
  void aCallTargetKeepsItsCopiedParameters() {
    final CallTarget target =
        new CallTarget(
            STRING, "equals", List.of(TypeRef.of("java.lang.Object")), null, false, false);

    assertThat(target.parameters()).containsExactly(TypeRef.of("java.lang.Object"));
    assertThatThrownBy(() -> target.parameters().add(STRING))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsInvalidValues() {
    assertThatThrownBy(() -> new TypeRef(" ", List.of(), 0, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ConstantValue(new Object()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static SpanFacts withType(final TypeRef type) {
    return new SpanFacts(type, null, null, List.of(), List.of(), List.of(), List.of(), null);
  }
}
