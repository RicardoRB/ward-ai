package dev.ward.analysis.domain.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExpressionTypesTest {
  private static final Span AT = new Span(1, 1, 1, 5, 0, 4);
  private static final Span LATER = new Span(2, 1, 2, 2, 10, 11);

  @Test
  void copiesTheMapSoLaterChangesDoNotReachTheRecord() {
    final Map<Span, String> source = new HashMap<>();
    source.put(AT, "java.lang.String");

    final ExpressionTypes types = new ExpressionTypes(source);
    source.put(LATER, "int");

    assertThat(types.types()).containsOnly(Map.entry(AT, "java.lang.String"));
  }

  @Test
  void rejectsMissingMap() {
    assertThatThrownBy(() -> new ExpressionTypes(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsNullTypeName() {
    final Map<Span, String> withNull = new HashMap<>();
    withNull.put(AT, null);

    assertThatThrownBy(() -> new ExpressionTypes(withNull))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void noneAnswersNoSpan() {
    assertThat(ExpressionTypes.none().types()).isEmpty();
  }
}
