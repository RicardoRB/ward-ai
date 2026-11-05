package dev.ward.bench;

import java.util.List;

/**
 * The accuracy of one rule on one corpus: true positives, false positives and false negatives.
 * Precision and recall are null when their denominator is zero.
 *
 * @param rule the rule id, or {@code total} for a sum over rules
 * @param truePositives the vulnerable files with a finding
 * @param falsePositives the files with a finding that are not labeled vulnerable
 * @param falseNegatives the vulnerable files without a finding
 */
record RuleScore(String rule, int truePositives, int falsePositives, int falseNegatives) {

  /** The name of a score that sums the rules of a corpus or of several corpora. */
  static final String TOTAL = "total";

  /**
   * Adds scores of the same rule.
   *
   * @param rule the rule id of the sum
   * @param scores the scores to add
   * @return the sum
   */
  static RuleScore sum(final String rule, final List<RuleScore> scores) {
    int truePositives = 0;
    int falsePositives = 0;
    int falseNegatives = 0;
    for (final RuleScore score : scores) {
      truePositives += score.truePositives();
      falsePositives += score.falsePositives();
      falseNegatives += score.falseNegatives();
    }
    return new RuleScore(rule, truePositives, falsePositives, falseNegatives);
  }

  /** Returns tp / (tp + fp), or null when no finding was reported. */
  Double precision() {
    return ratio(truePositives, truePositives + falsePositives);
  }

  /** Returns tp / (tp + fn), or null when no file is labeled vulnerable. */
  Double recall() {
    return ratio(truePositives, truePositives + falseNegatives);
  }

  private static Double ratio(final int numerator, final int denominator) {
    return denominator == 0 ? null : (double) numerator / denominator;
  }
}
