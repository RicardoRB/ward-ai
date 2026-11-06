package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Proves that the fixture comparison can fail, and says what differs when it does. */
class FixtureComparisonTest {

  private static final int LINE = 2;
  private static final int COLUMN = 3;
  private static final int OTHER_LINE = 5;

  private static final String RULE = "ward:java/method-length";
  private static final String FILE = "violating.java";

  @Test
  void aMissingFindingNamesTheRuleTheLanguageAndTheLine() {
    final List<String> problems =
        FixtureComparison.problems(RULE, AnalysisLanguage.JAVA, FILE, List.of("4:1"), List.of());

    assertThat(problems)
        .containsExactly(
            "ward:java/method-length (java): violating.java expects a finding at line 4,"
                + " column 1 but the rule did not report it");
  }

  @Test
  void anUnexpectedFindingInAPassingFixtureFails() {
    final List<String> problems =
        FixtureComparison.problems(
            RULE, AnalysisLanguage.JAVA, "passing.java", List.of(), List.of(finding(LINE, COLUMN)));

    assertThat(problems)
        .containsExactly(
            "ward:java/method-length (java): passing.java reports an unexpected finding at"
                + " line 2, column 3");
  }

  @Test
  void matchingPositionsGiveNoProblem() {
    assertThat(
            FixtureComparison.problems(
                RULE,
                AnalysisLanguage.JAVA,
                FILE,
                List.of("2:3", "5:1"),
                List.of(finding(LINE, COLUMN), finding(OTHER_LINE, 1))))
        .isEmpty();
  }

  @Test
  void theSamePositionsInAnotherOrderFail() {
    final List<String> problems =
        FixtureComparison.problems(
            RULE,
            AnalysisLanguage.JAVA,
            FILE,
            List.of("2:3", "5:1"),
            List.of(finding(OTHER_LINE, 1), finding(LINE, COLUMN)));

    assertThat(problems).hasSize(1).first().asString().contains("reports [5:1, 2:3]");
  }

  @Test
  void aTypeFactsFileIsReportedUntilTheLoaderExists() {
    assertThat(FixtureTypeFacts.problem(Optional.empty())).isEmpty();
    assertThat(FixtureTypeFacts.problem(Optional.of(java.nio.file.Path.of("types.json"))))
        .hasValueSatisfying(message -> assertThat(message).contains("#350"));
  }

  private static AnalysisFinding finding(final int line, final int column) {
    return new AnalysisFinding(
        RULE, AnalysisSeverity.ERROR, "message", FILE, line, column, null, List.of());
  }
}
