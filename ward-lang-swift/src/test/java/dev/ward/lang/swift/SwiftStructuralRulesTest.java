package dev.ward.lang.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The Swift structural rules over one violating and one passing fixture per rule. */
final class SwiftStructuralRulesTest {

  private static final String FIXTURES = "fixtures/swift-structural/";

  @ParameterizedTest
  @ValueSource(
      strings = {
        "function-length",
        "parameter-count",
        "complexity",
        "file-length",
        "type-body-length"
      })
  void violatingFixtureReportsOneError(final String rule) throws IOException {
    final RuleRunResult result =
        SwiftRuleRuns.run(rule, SwiftRuleRuns.read(FIXTURES + rule + "/violating.swift"));

    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().get(0).ruleId()).isEqualTo(SwiftRuleRuns.PREFIX + rule);
    assertThat(result.findings().get(0).severity()).isEqualTo(AnalysisSeverity.ERROR);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "function-length",
        "parameter-count",
        "complexity",
        "file-length",
        "type-body-length",
        "naming"
      })
  void passingFixtureReportsNothing(final String rule) throws IOException {
    final RuleRunResult result =
        SwiftRuleRuns.run(rule, SwiftRuleRuns.read(FIXTURES + rule + "/passing.swift"));

    assertThat(result.findings()).isEmpty();
    assertThat(result.executionErrors()).isEmpty();
  }

  @Test
  void namingViolatingFixtureReportsWarningsWithExpectedStyle() throws IOException {
    final List<AnalysisFinding> findings =
        SwiftRuleRuns.run("naming", SwiftRuleRuns.read(FIXTURES + "naming/violating.swift"))
            .findings();

    assertThat(findings)
        .extracting(AnalysisFinding::message)
        .containsExactly(
            "userStore should be CapWords",
            "MyVar should be lowerCamelCase",
            "load_config should be lowerCamelCase");
    assertThat(findings).allMatch(finding -> finding.severity() == AnalysisSeverity.WARNING);
  }

  @Test
  void functionLengthFindingPointsAtTheDeclarationLine() throws IOException {
    final AnalysisFinding finding =
        SwiftRuleRuns.run(
                "function-length", SwiftRuleRuns.read(FIXTURES + "function-length/violating.swift"))
            .findings()
            .get(0);

    assertThat(finding.line()).isEqualTo(1);
    assertThat(finding.message()).isEqualTo("function longRunning spans 61 lines, the limit is 60");
  }

  @Test
  void parameterCountCountsEveryParameterOfAMethod() throws IOException {
    final AnalysisFinding finding =
        SwiftRuleRuns.run(
                "parameter-count", SwiftRuleRuns.read(FIXTURES + "parameter-count/violating.swift"))
            .findings()
            .get(0);

    assertThat(finding.message()).isEqualTo("function process has 6 parameters, the limit is 5");
  }

  @Test
  void complexityCountsTheDecisionsOfTheFunction() throws IOException {
    final AnalysisFinding finding =
        SwiftRuleRuns.run("complexity", SwiftRuleRuns.read(FIXTURES + "complexity/violating.swift"))
            .findings()
            .get(0);

    assertThat(finding.message()).isEqualTo("function classify has complexity 11, the limit is 10");
  }

  @Test
  void typeBodyLengthCountsTheHeaderLinesOfTheType() throws IOException {
    final AnalysisFinding finding =
        SwiftRuleRuns.run(
                "type-body-length",
                SwiftRuleRuns.read(FIXTURES + "type-body-length/violating.swift"))
            .findings()
            .get(0);

    assertThat(finding.line()).isEqualTo(1);
    assertThat(finding.message()).isEqualTo("type Store body spans 301 lines, the limit is 300");
  }

  @Test
  void syntaxErrorIsAnExecutionErrorNotAFinding() {
    final RuleRunResult result =
        SwiftRuleRuns.run("function-length", "func broken( {\n    return\n");

    assertThat(result.findings()).isEmpty();
    assertThat(result.executionErrors()).isNotEmpty();
  }

  @Test
  void rulesFileDefinesTheSixRulesInOrder() {
    final List<RuleDefinition> rules = SwiftRuleRuns.rules();

    assertThat(rules)
        .extracting(RuleDefinition::id)
        .containsExactly(
            SwiftRuleRuns.PREFIX + "function-length",
            SwiftRuleRuns.PREFIX + "parameter-count",
            SwiftRuleRuns.PREFIX + "complexity",
            SwiftRuleRuns.PREFIX + "file-length",
            SwiftRuleRuns.PREFIX + "type-body-length",
            SwiftRuleRuns.PREFIX + "naming");
  }

  @Test
  void errorRulesAndTheWarningRuleHaveTheirSeverities() {
    assertThat(SwiftRuleRuns.rules())
        .filteredOn(rule -> !rule.id().endsWith("/naming"))
        .allMatch(rule -> rule.severity() == AnalysisSeverity.ERROR);
    assertThat(SwiftRuleRuns.rules())
        .filteredOn(rule -> rule.id().endsWith("/naming"))
        .allMatch(rule -> rule.severity() == AnalysisSeverity.WARNING);
  }
}
