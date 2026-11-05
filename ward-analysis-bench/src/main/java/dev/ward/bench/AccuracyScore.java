package dev.ward.bench;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Scores one rule against the labels of a corpus. A finding counts per file: a file with a
 * vulnerable label and a finding is a true positive, a file with a finding and no vulnerable label
 * is a false positive, and a vulnerable file without a finding is a false negative.
 */
final class AccuracyScore {

  private AccuracyScore() {}

  /**
   * Scores a rule.
   *
   * @param rule the rule id to score
   * @param labels the labels of the corpus, for all rules
   * @param findings the findings of the corpus, for all rules
   * @return the counts of the rule
   */
  static RuleScore score(
      final String rule, final Collection<AccuracyLabel> labels, final Set<WardFinding> findings) {
    final Set<String> vulnerable = new HashSet<>();
    for (final AccuracyLabel label : labels) {
      if (label.rule().equals(rule) && label.vulnerable()) {
        vulnerable.add(label.file());
      }
    }
    final Set<String> flagged = new HashSet<>();
    for (final WardFinding finding : findings) {
      if (finding.rule().equals(rule)) {
        flagged.add(finding.file());
      }
    }
    final Set<String> hits = new HashSet<>(flagged);
    hits.retainAll(vulnerable);
    final int falsePositives = flagged.size() - hits.size();
    final int falseNegatives = vulnerable.size() - hits.size();
    return new RuleScore(rule, hits.size(), falsePositives, falseNegatives);
  }
}
