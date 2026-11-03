package dev.ward.lang.python;

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

/** The Python structural rules over one violating and one passing fixture per rule. */
final class PythonStructuralRulesTest {

  private static final String FIXTURES = "fixtures/python-structural/";

  @ParameterizedTest
  @ValueSource(strings = {"function-length", "parameter-count", "complexity", "file-length"})
  void violatingFixtureReportsOneError(final String rule) throws IOException {
    final RuleRunResult result =
        PythonRuleRuns.run(rule, PythonRuleRuns.read(FIXTURES + rule + "/violating.py"));

    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().get(0).ruleId()).isEqualTo(PythonRuleRuns.PREFIX + rule);
    assertThat(result.findings().get(0).severity()).isEqualTo(AnalysisSeverity.ERROR);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"function-length", "parameter-count", "complexity", "file-length", "naming"})
  void passingFixtureReportsNothing(final String rule) throws IOException {
    final RuleRunResult result =
        PythonRuleRuns.run(rule, PythonRuleRuns.read(FIXTURES + rule + "/passing.py"));

    assertThat(result.findings()).isEmpty();
    assertThat(result.executionErrors()).isEmpty();
  }

  @Test
  void functionLengthFindingPointsAtDefLine() throws IOException {
    final AnalysisFinding finding =
        PythonRuleRuns.run(
                "function-length", PythonRuleRuns.read(FIXTURES + "function-length/violating.py"))
            .findings()
            .get(0);

    assertThat(finding.line()).isEqualTo(1);
    assertThat(finding.message())
        .isEqualTo("function long_running spans 61 lines, the limit is 60");
  }

  @Test
  void namingReportsEachBadNameWithItsExpectedStyle() throws IOException {
    final List<AnalysisFinding> findings =
        PythonRuleRuns.run("naming", PythonRuleRuns.read(FIXTURES + "naming/violating.py"))
            .findings();

    assertThat(findings)
        .extracting(AnalysisFinding::message)
        .containsExactly("loadConfig should be snake_case", "user_store should be CapWords");
    assertThat(findings).allMatch(finding -> finding.severity() == AnalysisSeverity.WARNING);
  }

  @Test
  void syntaxErrorIsAnExecutionErrorNotAFinding() {
    final RuleRunResult result = PythonRuleRuns.run("function-length", "def broken(:\n    pass\n");

    assertThat(result.findings()).isEmpty();
    assertThat(result.executionErrors()).isNotEmpty();
  }

  @Test
  void rulesFileDefinesTheFiveRulesInOrder() {
    final List<RuleDefinition> rules = PythonRuleRuns.rules();

    assertThat(rules)
        .extracting(RuleDefinition::id)
        .containsExactly(
            PythonRuleRuns.PREFIX + "function-length",
            PythonRuleRuns.PREFIX + "parameter-count",
            PythonRuleRuns.PREFIX + "complexity",
            PythonRuleRuns.PREFIX + "file-length",
            PythonRuleRuns.PREFIX + "naming");
  }
}
