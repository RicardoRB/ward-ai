package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Java data-flow scenarios of the intraprocedural spec, on lowered Java sources. */
final class JavaDataFlowScenarioTest {

  @Test
  void twoBranchesReachTheReadAfterThem() {
    final String source =
        "class A { void f(boolean a) { int x; if (a) { x = 1; } else { x = 2; } use(x); } }";

    assertThat(DataFlowFixtures.reachingLines(DataFlowFixtures.java(source, "A#f(1)"), "x"))
        .containsExactly(1, 1);
    assertThat(DataFlowFixtures.reachingLines(DataFlowFixtures.java(source, "A#f(1)"), "x"))
        .hasSize(2);
  }

  @Test
  void aLaterWriteHidesTheOneBeforeIt() {
    final String source = "class A { void f() { int x = 1; x = 2; use(x); } }";

    assertThat(DataFlowFixtures.reachingLines(DataFlowFixtures.java(source, "A#f(0)"), "x"))
        .hasSize(1);
  }

  @Test
  void aWriteInTheTryReachesTheCatchBlock() {
    final String source =
        "class A { void f() { int x = 0; try { x = 1; risky(); } catch (Exception e) { use(x); } } }";

    assertThat(DataFlowFixtures.reachingLines(DataFlowFixtures.java(source, "A#f(0)"), "x"))
        .hasSize(2);
  }

  @Test
  void anIncrementReadsAndWritesAndTheLoopCarriesItToTheCondition() {
    final String source = "class A { void f(int n) { for (int i = 0; i < n; i++) { use(i); } } }";

    assertThat(DataFlowFixtures.reachingLines(DataFlowFixtures.java(source, "A#f(1)"), "i"))
        .hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void javaLambdaCaptureIsNotReported() {
    final String source = "class A { void f() { int x = 1; Runnable r = () -> use(x); } }";

    assertThat(DataFlowFixtures.unusedFindings(AnalysisLanguage.JAVA, source)).isEmpty();
  }

  @Test
  void aJavaForEachVariableIsReachedOnlyByItsBindingWrite() {
    final String source =
        "class A { void f(List<String> items) { for (String s : items) { use(s); } } }";

    DataFlowFixtures.assertBindingWriteReaches(
        source,
        DataFlowFixtures.reachingWrites(DataFlowFixtures.java(source, "A#f(1)"), "s"),
        "s",
        "use(s)");
  }

  @Test
  void aJavaCatchParameterIsReachedOnlyByItsWrite() {
    final String source =
        "class A { void f() { try { risky(); } catch (RuntimeException e) { use(e); } } }";

    DataFlowFixtures.assertBindingWriteReaches(
        source,
        DataFlowFixtures.reachingWrites(DataFlowFixtures.java(source, "A#f(0)"), "e"),
        "e",
        "use(e)");
  }

  /**
   * A deliberate divergence from PMD: PMD 7.29.0 does not report the dead initializer of a switch
   * whose case and default both assign, while the same shape with if/else is reported. Ward reports
   * it, because the initializer is overwritten on every path. This test keeps that behaviour, which
   * the PMD reference fixtures do not cover.
   */
  @Test
  void deliberatelyDivergesFromPmdAndReportsTheDeadInitializerOfASwitchWithAnAssigningDefault() {
    final String source =
        "class A { int f(int k) { int x = 0; switch (k) { case 1: x = 1; break; default: x = 2; } return x; } }";

    final List<AnalysisFinding> findings =
        DataFlowFixtures.unusedFindings(AnalysisLanguage.JAVA, source);

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).isEqualTo("the value assigned to 'x' is never read");
    assertThat(findings.get(0).line()).isEqualTo(1);
  }
}
