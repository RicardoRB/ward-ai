package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Scores one rule per file: true, false positive and false negative counts, and their ratios. */
class AccuracyScoreTest {

  private static final String RULE = "ward:java/sql-injection";
  private static final String OTHER = "ward:java/command-injection";

  @Test
  void countsEachFileOnceAndTheLabelsDecideTheClass() {
    final List<AccuracyLabel> labels =
        List.of(
            new AccuracyLabel("a.java", RULE, true),
            new AccuracyLabel("b.java", RULE, false),
            new AccuracyLabel("c.java", RULE, true));
    final Set<WardFinding> findings =
        Set.of(
            new WardFinding("a.java", RULE),
            new WardFinding("b.java", RULE),
            new WardFinding("d.java", RULE));

    final RuleScore score = AccuracyScore.score(RULE, labels, findings);

    assertThat(score.truePositives()).isEqualTo(1);
    assertThat(score.falsePositives()).isEqualTo(2);
    assertThat(score.falseNegatives()).isEqualTo(1);
    assertThat(score.precision()).isEqualTo(1.0 / 3);
    assertThat(score.recall()).isEqualTo(0.5);
  }

  @Test
  void findingsOfOtherRulesDoNotCount() {
    final List<AccuracyLabel> labels = List.of(new AccuracyLabel("a.java", RULE, true));
    final Set<WardFinding> findings = Set.of(new WardFinding("a.java", OTHER));

    final RuleScore score = AccuracyScore.score(RULE, labels, findings);

    assertThat(score.truePositives()).isZero();
    assertThat(score.falsePositives()).isZero();
    assertThat(score.falseNegatives()).isEqualTo(1);
  }

  @Test
  void precisionIsNullWhenNothingIsReported() {
    final RuleScore score =
        AccuracyScore.score(RULE, List.of(new AccuracyLabel("a.java", RULE, true)), Set.of());

    assertThat(score.precision()).isNull();
    assertThat(score.recall()).isZero();
  }

  @Test
  void recallIsNullWhenNoFileIsVulnerable() {
    final RuleScore score =
        AccuracyScore.score(
            RULE,
            List.of(new AccuracyLabel("a.java", RULE, false)),
            Set.of(new WardFinding("a.java", RULE)));

    assertThat(score.recall()).isNull();
    assertThat(score.precision()).isZero();
  }

  @Test
  void sumAddsTheCountsOfTheRule() {
    final RuleScore sum =
        RuleScore.sum(
            RuleScore.TOTAL, List.of(new RuleScore(RULE, 1, 2, 3), new RuleScore(OTHER, 4, 5, 6)));

    assertThat(sum).isEqualTo(new RuleScore(RuleScore.TOTAL, 5, 7, 9));
  }
}
