package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The TypeScript and JavaScript data-flow scenarios of the intraprocedural spec. */
final class TypeScriptDataFlowScenarioTest {

  @Test
  void typeScriptBranchesReachTheReadAfterThem() {
    final String source =
        "function f(a: boolean) { let x: number; if (a) { x = 1; } else { x = 2; } use(x); }";

    assertThat(DataFlowFixtures.reachingLines(DataFlowFixtures.typescript(source, "f"), "x"))
        .hasSize(2);
  }

  @Test
  void typeScriptOverwrittenValueIsReportedThroughTheRule() {
    final String source = "function f() { let x = 1; x = 2; use(x); }";

    final List<AnalysisFinding> findings =
        DataFlowFixtures.unusedFindings(AnalysisLanguage.TYPESCRIPT, source);

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).isEqualTo("the value assigned to 'x' is never read");
  }

  @Test
  void typeScriptFieldAndMemberWritesAreNotReported() {
    final String source = "function f(obj: any) { this.count = 1; obj.field = 2; }";

    assertThat(DataFlowFixtures.unusedFindings(AnalysisLanguage.TYPESCRIPT, source)).isEmpty();
  }

  @Test
  void aTypeScriptForOfVariableIsReachedOnlyByItsBindingWrite() {
    final String source = "function f(items) { for (const item of items) { use(item); } }";

    DataFlowFixtures.assertBindingWriteReaches(
        source,
        DataFlowFixtures.reachingWrites(DataFlowFixtures.typescript(source, "f"), "item"),
        "item",
        "use(item)");
  }

  @Test
  void aTypeScriptCatchParameterIsReachedOnlyByItsWrite() {
    final String source = "function f() { try { risky(); } catch (err) { use(err); } }";

    DataFlowFixtures.assertBindingWriteReaches(
        source,
        DataFlowFixtures.reachingWrites(DataFlowFixtures.typescript(source, "f"), "err"),
        "err",
        "use(err)");
  }

  @Test
  void aJavaScriptForInVariableIsReachedOnlyByItsBindingWrite() {
    final String source = "function f(obj) { for (const key in obj) { use(key); } }";

    DataFlowFixtures.assertBindingWriteReaches(
        source,
        DataFlowFixtures.reachingWrites(DataFlowFixtures.javaScript(source, "f"), "key"),
        "key",
        "use(key)");
  }
}
