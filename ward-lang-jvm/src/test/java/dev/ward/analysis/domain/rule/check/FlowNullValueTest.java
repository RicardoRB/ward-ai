package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.Span;
import org.junit.jupiter.api.Test;

class FlowNullValueTest {

  @Test
  void joiningEqualValuesKeepsThem() {
    for (final FlowNullValue value : FlowNullValue.values()) {
      assertThat(value.join(value)).isEqualTo(value);
    }
  }

  @Test
  void unknownAbsorbsEverything() {
    assertThat(FlowNullValue.NULL.join(FlowNullValue.UNKNOWN)).isEqualTo(FlowNullValue.UNKNOWN);
    assertThat(FlowNullValue.UNKNOWN.join(FlowNullValue.NON_NULL)).isEqualTo(FlowNullValue.UNKNOWN);
  }

  @Test
  void nullAndNonNullMakeMixed() {
    assertThat(FlowNullValue.NULL.join(FlowNullValue.NON_NULL)).isEqualTo(FlowNullValue.MIXED);
    assertThat(FlowNullValue.MIXED.join(FlowNullValue.NULL)).isEqualTo(FlowNullValue.MIXED);
  }

  @Test
  void twoNullishOrTwoNonNullValuesStayThatWay() {
    assertThat(FlowNullValue.NEVER.join(FlowNullValue.NULL)).isEqualTo(FlowNullValue.NULL);
    assertThat(FlowNullValue.DEREFERENCED.join(FlowNullValue.NON_NULL))
        .isEqualTo(FlowNullValue.NON_NULL);
  }

  @Test
  void aNullableValueJoinedWithAKnownOneStaysNullable() {
    assertThat(FlowNullValue.NULLABLE.join(FlowNullValue.NON_NULL))
        .isEqualTo(FlowNullValue.NULLABLE);
  }

  @Test
  void aVariableOnOnePathOnlyIsKeptAndTheExceptionFlagNeedsBothPaths() {
    final Span declared = new Span(1, 1, 1, 2, 0, 1);
    final FlowNullState left = new FlowNullState();
    left.put(declared, FlowNullValue.NULL, true);
    final FlowNullState right = new FlowNullState();
    assertThat(left.join(right).get(declared).value()).isEqualTo(FlowNullValue.NULL);
    right.put(declared, FlowNullValue.NULL, false);
    assertThat(left.join(right).get(declared).exception()).isFalse();
    assertThat(left.onException().get(declared).exception()).isTrue();
    assertThat(left.copy()).isEqualTo(left).hasSameHashCodeAs(left);
  }
}
