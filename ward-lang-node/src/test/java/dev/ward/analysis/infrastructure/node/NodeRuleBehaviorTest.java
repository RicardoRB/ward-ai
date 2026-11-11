package dev.ward.analysis.infrastructure.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavior of the structural rules on inline sources, for the edge cases the spec names. */
class NodeRuleBehaviorTest {

  private static final AnalysisLanguage TS = AnalysisLanguage.TYPESCRIPT;
  private static final AnalysisLanguage JS = AnalysisLanguage.JAVASCRIPT;
  private static final int BLOCK_LINE = 3;

  @Test
  void complexityCountsEachBranchAndLogicalOperator() {
    final String source = "function f(a, b) {\n  if (a && b) {\n    void 0;\n  }\n}\n";

    assertThat(NodeRuleRuns.run("complexity", TS, "max=2", source).findings()).hasSize(1);
    assertThat(NodeRuleRuns.run("complexity", TS, "max=3", source).findings()).isEmpty();
  }

  @Test
  void nestedFunctionCountsItsOwnDecisionsNotTheOuterFunctions() {
    final String source =
        "function outer() {\n"
            + "  const inner = (a) => {\n"
            + "    if (a) { void 0; }\n"
            + "    if (a) { void 0; }\n"
            + "    if (a) { void 0; }\n"
            + "  };\n"
            + "  return inner;\n"
            + "}\n";

    final RuleRunResult result = NodeRuleRuns.run("complexity", TS, "max=3", source);

    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().get(0).line()).isEqualTo(2);
  }

  @Test
  void measuresReturnsEveryFunctionIncludingNestedOnesWithinTheLimit() {
    final String source =
        "function outer(a) {\n"
            + "  const inner = (b) => {\n"
            + "    if (b) { void 0; }\n"
            + "  };\n"
            + "  return inner;\n"
            + "}\n";

    for (final AnalysisLanguage language : List.of(TS, JS)) {
      assertThat(NodeRuleRuns.measure("complexity", language, source))
          .extracting(measured -> measured.span().startLine() + ":" + measured.value())
          .containsExactly("1:1", "2:2");
    }
  }

  @Test
  void destructuredArrowParametersCountOnceEach() {
    final String source = "const g = (a, { b }) => a + b;\n";

    assertThat(NodeRuleRuns.run("parameter-count", TS, "max=1", source).findings()).hasSize(1);
    assertThat(NodeRuleRuns.run("parameter-count", TS, "max=2", source).findings()).isEmpty();
  }

  @Test
  void elseIfChainDoesNotAddNestingDepth() {
    final String source =
        "function f(a) {\n"
            + "  if (a) { void 0; }\n"
            + "  else if (a > 1) { void 0; }\n"
            + "  else if (a > 2) { void 0; }\n"
            + "  else if (a > 3) { void 0; }\n"
            + "  else if (a > 4) { void 0; }\n"
            + "  else if (a > 5) { void 0; }\n"
            + "}\n";

    assertThat(NodeRuleRuns.run("nesting-depth", JS, "", source).findings()).isEmpty();
  }

  @Test
  void blockBeyondTheLimitIsReportedAtItsOwnLine() {
    final String source =
        "function f(a) {\n"
            + "  if (a) {\n"
            + "    if (a) {\n"
            + "      void 0;\n"
            + "    }\n"
            + "  }\n"
            + "}\n";

    final RuleRunResult result = NodeRuleRuns.run("nesting-depth", TS, "max=1", source);

    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().get(0).line()).isEqualTo(BLOCK_LINE);
  }

  @Test
  void blankAndCommentLinesCountTowardFunctionLength() {
    final String source = "function f() {\n\n  // note\n  void 0;\n}\n";

    assertThat(NodeRuleRuns.run("function-length", TS, "max=4", source).findings()).hasSize(1);
    assertThat(NodeRuleRuns.run("function-length", TS, "max=5", source).findings()).isEmpty();
  }

  @Test
  void esmAndCommonJsFunctionsMeasureTheSame() {
    final String esm = "export function f() {\n  void 0;\n  void 1;\n}\n";
    final String cjs = "module.exports.f = function () {\n  void 0;\n  void 1;\n};\n";

    final RuleRunResult fromEsm = NodeRuleRuns.run("function-length", JS, "max=2", esm);
    final RuleRunResult fromCjs = NodeRuleRuns.run("function-length", JS, "max=2", cjs);

    assertThat(fromEsm.findings()).hasSize(1);
    assertThat(fromCjs.findings()).hasSize(1);
    assertThat(fromEsm.findings().get(0).message()).isEqualTo(fromCjs.findings().get(0).message());
  }

  @Test
  void unitWithSyntaxErrorsIsNotCheckedAndIsReportedAsAnExecutionError() {
    final String source = "function f() {\n  void 0;\n  void 1;\n}\nconst = ;\n";

    final RuleRunResult result = NodeRuleRuns.run("function-length", TS, "max=2", source);

    assertThat(result.findings()).isEmpty();
    assertThat(result.executionErrors())
        .singleElement()
        .satisfies(error -> assertThat(error.reason()).isEqualTo("the unit has syntax errors"));
  }
}
