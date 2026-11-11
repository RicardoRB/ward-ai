package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class CrapScoreTest {

  private static final double EXACT = 1e-9;

  @Test
  void anUntestedFunctionScoresItsComplexitySquaredPlusItself() {
    assertThat(CrapScore.of(6, OptionalDouble.of(0)).score()).isEqualTo(42);
    assertThat(CrapScore.of(5, OptionalDouble.of(0)).score()).isEqualTo(30);
  }

  @Test
  void aPartlyTestedFunctionScoresBetween() {
    assertThat(CrapScore.of(10, OptionalDouble.of(0.4)).score()).isCloseTo(31.6, within(EXACT));
  }

  @Test
  void aFullyTestedFunctionScoresItsComplexity() {
    assertThat(CrapScore.of(8, OptionalDouble.of(1)).score()).isEqualTo(8);
  }

  @Test
  void aFunctionWithoutCoverageDataScoresItsComplexityAlone() {
    final CrapScore score = CrapScore.of(9, OptionalDouble.empty());

    assertThat(score.score()).isEqualTo(9);
    assertThat(score.coverageDisplay()).isEqualTo("no coverage data");
  }

  @Test
  void displaysOneDecimalWithoutATrailingZero() {
    assertThat(CrapScore.of(6, OptionalDouble.of(0)).display()).isEqualTo("42");
    assertThat(CrapScore.of(10, OptionalDouble.of(0.4)).display()).isEqualTo("31.6");
    assertThat(CrapScore.of(6, OptionalDouble.of(0)).coverageDisplay()).isEqualTo("0%");
    assertThat(CrapScore.of(6, OptionalDouble.of(0.5)).coverageDisplay()).isEqualTo("50%");
  }
}
