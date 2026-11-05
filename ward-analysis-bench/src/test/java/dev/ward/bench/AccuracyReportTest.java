package dev.ward.bench;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Sums the corpora, and renders the JSON and the table with null ratios. */
class AccuracyReportTest {

  private static final String SQL = "ward:java/sql-injection";
  private static final String PATH = "ward:java/path-injection";

  @Test
  void sumsEachRuleOverTheCorpora() {
    final AccuracyResult result =
        AccuracyResult.of(
            List.of(
                new CorpusAccuracy(
                    "one", CorpusAccuracy.CHECKED_IN, List.of(new RuleScore(SQL, 1, 0, 1))),
                new CorpusAccuracy(
                    "two",
                    "0123",
                    List.of(new RuleScore(PATH, 2, 1, 0), new RuleScore(SQL, 0, 1, 2)))));

    assertThat(result.rules())
        .containsExactly(new RuleScore(PATH, 2, 1, 0), new RuleScore(SQL, 1, 1, 3));
    assertThat(result.total()).isEqualTo(new RuleScore(RuleScore.TOTAL, 3, 2, 3));
  }

  @Test
  void jsonNamesTheModeAndWritesNullRatios() {
    final AccuracyResult result =
        AccuracyResult.of(
            List.of(
                new CorpusAccuracy(
                    "one", CorpusAccuracy.CHECKED_IN, List.of(new RuleScore(SQL, 0, 0, 1)))));

    final String json = AccuracyJson.render(result, Instant.parse("2026-01-01T00:00:00Z"));

    assertThat(json)
        .contains("\"schemaVersion\": 2")
        .contains("\"mode\": \"accuracy\"")
        .contains("\"name\": \"one\"")
        .contains("\"precision\": null")
        .contains("\"recall\": 0.0");
  }

  @Test
  void tableWritesNotApplicableForANullRatio() {
    final AccuracyResult result =
        AccuracyResult.of(
            List.of(
                new CorpusAccuracy(
                    "one", CorpusAccuracy.CHECKED_IN, List.of(new RuleScore(SQL, 0, 0, 1)))));

    assertThat(AccuracySummary.render(result)).contains("n/a").contains("all corpora");
  }
}
