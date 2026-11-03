package dev.ward.lang.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Edge cases of the Python structural rules, written inline so each one reads with its source. */
final class PythonStructuralEdgeCasesTest {

  private static final int NESTED_CAPACITY = 256;
  private static final int NESTED_DECISIONS = 10;

  @Test
  void receiverOfAMethodIsNotCounted() {
    assertThat(
            PythonRuleRuns.run(
                    "parameter-count", "class A:\n    def m(self, a, b, c, d, e):\n        pass\n")
                .findings())
        .isEmpty();
    assertThat(
            PythonRuleRuns.run(
                    "parameter-count",
                    "class A:\n    def m(self, a, b, c, d, e, g):\n        pass\n")
                .findings())
        .hasSize(1);
  }

  @Test
  void selfOfATopLevelFunctionIsCounted() {
    assertThat(
            PythonRuleRuns.run("parameter-count", "def f(self, a, b, c, d):\n    pass\n")
                .findings())
        .isEmpty();
    assertThat(
            PythonRuleRuns.run("parameter-count", "def f(self, a, b, c, d, e):\n    pass\n")
                .findings())
        .hasSize(1);
  }

  @Test
  void keywordOnlyParametersAreCounted() {
    assertThat(
            PythonRuleRuns.run("parameter-count", "def f(a, b, c, *, d, e, g):\n    pass\n")
                .findings())
        .hasSize(1);
  }

  @Test
  void complexityCountsEveryDecisionKind() {
    final String source =
        """
        def route(x, y, z, w):
            if x:
                pass
            elif y:
                pass
            elif z:
                pass
            while w:
                pass
            try:
                pass
            except ValueError:
                pass
            values = [a for a in x if a and y or z]
            return values if w else None
        """;

    final List<AnalysisFinding> findings = PythonRuleRuns.run("complexity", source).findings();

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message())
        .isEqualTo("function route has complexity 11, the limit is 10");
  }

  @Test
  void nestedFunctionIsMeasuredOnItsOwnNotInTheOuterOne() {
    final StringBuilder inner = new StringBuilder(NESTED_CAPACITY).append("    def inner(v):\n");
    for (int i = 0; i < NESTED_DECISIONS; i++) {
      inner.append("        if v == ").append(i).append(":\n            pass\n");
    }

    final List<AnalysisFinding> findings =
        PythonRuleRuns.run("complexity", "def outer(v):\n" + inner).findings();

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).startsWith("function inner ");
  }

  @Test
  void dunderAndPrivateNamesAreNotReportedWhenTheyFollowTheStyle() {
    assertThat(
            PythonRuleRuns.run("naming", "class A:\n    def __init__(self):\n        self._x = 1\n")
                .findings())
        .isEmpty();
    assertThat(PythonRuleRuns.run("naming", "def _private_helper():\n    pass\n").findings())
        .isEmpty();
    assertThat(PythonRuleRuns.run("naming", "def _loadConfig():\n    pass\n").findings())
        .hasSize(1);
  }
}
