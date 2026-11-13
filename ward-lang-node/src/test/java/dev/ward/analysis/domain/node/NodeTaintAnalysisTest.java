package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintAnalysis;
import dev.ward.analysis.domain.flow.taint.TaintFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Runs the taint analysis on lowered TypeScript and JavaScript functions with a fake specification.
 * Sinks and sanitizers are member calls, which both grammars parse as calls.
 */
class NodeTaintAnalysisTest {

  /** Scenario: Direct flow. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void directFlowIsOneFindingWithTwoSteps(final AnalysisLanguage language) {
    final List<TaintFinding> found =
        run(language, "function run() { const id = source(); sink(id); }");
    assertThat(found).hasSize(1);
    assertThat(NodeTaintFixtures.messages(found.get(0).path()))
        .containsExactly("source: source", "sink: sink argument 0");
  }

  /** Scenario: Flow through assignments. The path is source, the assignment of b, and the sink. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void flowThroughAssignmentsHasThreeSteps(final AnalysisLanguage language) {
    final List<TaintFinding> found =
        run(language, "function run() { const a = source(); const b = 'x' + a; sink(b); }");
    assertThat(found).hasSize(1);
    assertThat(NodeTaintFixtures.messages(found.get(0).path()))
        .containsExactly("source: source", "assigned to b", "sink: sink argument 0");
  }

  /** Scenario: Overwritten before the sink. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void overwrittenBeforeSinkIsNotReported(final AnalysisLanguage language) {
    assertThat(run(language, "function run() { let v = source(); v = 'safe'; sink(v); }"))
        .isEmpty();
  }

  /** Scenario: Sanitized value. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void sanitizedValueIsNotReported(final AnalysisLanguage language) {
    assertThat(
            run(
                language,
                "function run() { const a = source(); const b = san.clean(a); sink(b); }"))
        .isEmpty();
  }

  /** Scenario: Sanitized on one branch only. The unsanitized branch reaches the sink. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void sanitizedOnOneBranchIsReportedThroughTheOtherBranch(final AnalysisLanguage language) {
    final List<TaintFinding> found =
        run(
            language,
            "function run() { let a = source(); let b = a;"
                + " if (a) { b = san.clean(a); } sink(b); }");
    assertThat(found).hasSize(1);
    assertThat(NodeTaintFixtures.messages(found.get(0).path()))
        .containsExactly("source: source", "assigned to b", "sink: sink argument 0");
  }

  /** Scenario: Unguarded argument. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void taintInAnUnguardedArgumentIsNotReported(final AnalysisLanguage language) {
    assertThat(run(language, "function run() { const a = source(); sink('safe', a); }")).isEmpty();
  }

  /** Scenario: Member read. The path starts at the member read. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void memberReadStartsThePath(final AnalysisLanguage language) {
    final List<TaintFinding> found =
        run(language, "function run() { const q = req.query; sink(q); }");
    assertThat(found).hasSize(1);
    assertThat(NodeTaintFixtures.messages(found.get(0).path()))
        .containsExactly("source: query", "sink: sink argument 0");
  }

  /** Scenario: Repeated run. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void repeatedRunsGiveIdenticalPaths(final AnalysisLanguage language) {
    final String body = "function run() { const a = source(); const b = 'x' + a; sink(b); }";
    assertThat(run(language, body)).isEqualTo(run(language, body));
  }

  /** Scenario: Annotated parameter (TypeScript decorators). The path starts at the parameter. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT"})
  void annotatedParameterStartsThePath(final AnalysisLanguage language) {
    final String source =
        "class Handler {\n  handle(@Untrusted input: string) {\n    sink(input);\n  }\n}\n";
    final List<TaintFinding> found =
        TaintAnalysis.run(
            NodeTaintFixtures.graph(language, source, "handle"), NodeTaintFixtures.SPEC);
    assertThat(found).hasSize(1);
    assertThat(NodeTaintFixtures.messages(found.get(0).path()))
        .containsExactly("source: Untrusted", "sink: sink argument 0");
  }

  /** A value returned by one function and passed to a sinking function of the same file. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void flowAcrossFunctionsOfOneFileIsReported(final AnalysisLanguage language) {
    final String source =
        "function read() { return source(); }\n"
            + "function put(v) { sink(v); }\n"
            + "function run() { const d = read(); put(d); }\n"
            + "function safe() { put('x'); }\n";
    assertThat(NodeTaintFixtures.runFile(language, source)).hasSize(1);
  }

  private static List<TaintFinding> run(final AnalysisLanguage language, final String source) {
    return TaintAnalysis.run(
        NodeTaintFixtures.graph(language, source, "run"), NodeTaintFixtures.SPEC);
  }
}
