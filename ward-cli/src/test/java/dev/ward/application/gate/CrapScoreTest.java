package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class CrapScoreTest {

  private static final double EXACT = 1e-9;
  private static final int SIX = 6;
  private static final int FIVE = 5;
  private static final int EIGHT = 8;
  private static final int NINE = 9;
  private static final int TEN = 10;
  private static final double UNTESTED_SIX = 42;
  private static final double UNTESTED_FIVE = 30;
  private static final double FOUR_TENTHS = 0.4;
  private static final double PARTLY_TESTED = 31.6;
  private static final double HALF = 0.5;

  @Test
  void anUntestedFunctionScoresItsComplexitySquaredPlusItself() {
    assertThat(CrapScore.of(SIX, OptionalDouble.of(0)).score()).isEqualTo(UNTESTED_SIX);
    assertThat(CrapScore.of(FIVE, OptionalDouble.of(0)).score()).isEqualTo(UNTESTED_FIVE);
  }

  @Test
  void aPartlyTestedFunctionScoresBetween() {
    assertThat(CrapScore.of(TEN, OptionalDouble.of(FOUR_TENTHS)).score())
        .isCloseTo(PARTLY_TESTED, within(EXACT));
  }

  @Test
  void aFullyTestedFunctionScoresItsComplexity() {
    assertThat(CrapScore.of(EIGHT, OptionalDouble.of(1)).score()).isEqualTo(EIGHT);
  }

  @Test
  void aFunctionWithoutCoverageDataScoresItsComplexityAlone() {
    final CrapScore score = CrapScore.of(NINE, OptionalDouble.empty());

    assertThat(score.score()).isEqualTo(NINE);
    assertThat(score.coverageDisplay()).isEqualTo("no coverage data");
  }

  @Test
  void displaysOneDecimalWithoutATrailingZero() {
    assertThat(CrapScore.of(SIX, OptionalDouble.of(0)).display()).isEqualTo("42");
    assertThat(CrapScore.of(TEN, OptionalDouble.of(FOUR_TENTHS)).display()).isEqualTo("31.6");
    assertThat(CrapScore.of(SIX, OptionalDouble.of(0)).coverageDisplay()).isEqualTo("0%");
    assertThat(CrapScore.of(SIX, OptionalDouble.of(HALF)).coverageDisplay()).isEqualTo("50%");
  }
}
