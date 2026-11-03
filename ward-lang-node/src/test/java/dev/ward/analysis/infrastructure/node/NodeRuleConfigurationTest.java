package dev.ward.analysis.infrastructure.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleRunResult;
import org.junit.jupiter.api.Test;

/** Naming behavior and the handling of rule bodies and ids that cannot be read. */
class NodeRuleConfigurationTest {

  private static final AnalysisLanguage TS = AnalysisLanguage.TYPESCRIPT;

  @Test
  void namingReportsDeclarationsAndNotReferences() {
    final String source = "const a_b = 1;\nvoid c_d;\n";

    assertThat(NodeRuleRuns.run("naming", TS, "", source).findings()).hasSize(1);
  }

  @Test
  void malformedThresholdBodyIsAnExecutionErrorForThatRule() {
    final RuleRunResult result =
        NodeRuleRuns.run("complexity", TS, "max=0", "function f() { void 0; }\n");

    assertThat(result.findings()).isEmpty();
    assertThat(result.executionErrors())
        .singleElement()
        .satisfies(error -> assertThat(error.ruleId()).isEqualTo("ward:typescript/complexity"));
  }

  @Test
  void namingRejectsAnyBody() {
    final RuleRunResult result = NodeRuleRuns.run("naming", TS, "max=3", "const a = 1;\n");

    assertThat(result.executionErrors()).hasSize(1);
  }

  @Test
  void unknownMetricIsAnExecutionErrorNotAnAbort() {
    final RuleRunResult result = NodeRuleRuns.run("unknown", TS, "", "const a = 1;\n");

    assertThat(result.executionErrors()).hasSize(1);
  }
}
