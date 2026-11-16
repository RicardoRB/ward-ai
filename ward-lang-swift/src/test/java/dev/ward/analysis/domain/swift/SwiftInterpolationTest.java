package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SwiftInterpolationTest {
  @Test
  void aLiteralWithoutInterpolationReadsNoName() {
    assertThat(SwiftInterpolation.names("\"plain (text) name\"")).isEmpty();
  }

  @Test
  void theNamesOfEverySegmentAreReadInOrder() {
    assertThat(SwiftInterpolation.names("\"took \\(duration)\\(unit) for \\(item.name)\""))
        .containsExactly("duration", "unit", "item");
  }

  @Test
  void aCallInASegmentReadsItsArgumentsAndNotItsLabels() {
    assertThat(SwiftInterpolation.names("\"got \\(String(describing: result)) and \\(count + 1)\""))
        .containsExactly("String", "result", "count");
  }

  @Test
  void aSegmentThatIsNotClosedIsReadToTheEndOfTheLiteral() {
    assertThat(SwiftInterpolation.names("\"open \\(value")).containsExactly("value");
  }
}
