package dev.ward.application.gate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalDouble;

/** One function's CRAP score and the numbers it comes from. */
record CrapScore(int complexity, OptionalDouble coverage, double score) {

  private static final int DECIMALS = 1;
  private static final double PERCENT = 100;

  /**
   * Scores a function: {@code complexity^2 * (1 - coverage)^3 + complexity}. Without coverage data
   * the function is scored by its complexity alone.
   */
  static CrapScore of(final int complexity, final OptionalDouble coverage) {
    final double uncovered = 1 - coverage.orElse(1);
    return new CrapScore(
        complexity,
        coverage,
        complexity * complexity * uncovered * uncovered * uncovered + complexity);
  }

  /** Returns the score for display: one decimal, without a trailing zero. */
  String display() {
    return BigDecimal.valueOf(score)
        .setScale(DECIMALS, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString();
  }

  /** Returns the coverage for display, such as {@code 40%}, or a note when there is no data. */
  String coverageDisplay() {
    return coverage.isPresent()
        ? Math.round(coverage.getAsDouble() * PERCENT) + "%"
        : "no coverage data";
  }
}
