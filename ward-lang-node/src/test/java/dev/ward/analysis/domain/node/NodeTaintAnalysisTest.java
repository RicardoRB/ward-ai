package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintAnalysis;
import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintFinding;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Runs the taint analysis on lowered TypeScript and JavaScript functions with a fake specification.
 * Sinks and sanitizers are member calls, which both grammars parse as calls.
 */
class NodeTaintAnalysisTest {

  private static final TaintSpec SPEC =
      new TaintSpec(
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "source", Optional.empty(), Optional.empty(), List.of()),
              new TaintEntry(
                  FlowSiteKind.MEMBER_READ,
                  "query",
                  Optional.empty(),
                  Optional.of("req"),
                  List.of()),
              new TaintEntry(
                  FlowSiteKind.PARAMETER,
                  "Untrusted",
                  Optional.empty(),
                  Optional.empty(),
                  List.of())),
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "sink", Optional.empty(), Optional.of("db"), List.of(0))),
          List.of(
              new TaintEntry(
                  FlowSiteKind.CALL, "clean", Optional.empty(), Optional.of("san"), List.of())),
          List.of());

  /** Scenario: Direct flow. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void directFlowIsOneFindingWithTwoSteps(final AnalysisLanguage language) {
    final List<TaintFinding> found =
        run(language, "function run() { const id = source(); db.sink(id); }");
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
        run(language, "function run() { const a = source(); const b = 'x' + a; db.sink(b); }");
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
    assertThat(run(language, "function run() { let v = source(); v = 'safe'; db.sink(v); }"))
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
                "function run() { const a = source(); const b = san.clean(a); db.sink(b); }"))
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
                + " if (a) { b = san.clean(a); } db.sink(b); }");
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
    assertThat(run(language, "function run() { const a = source(); db.sink('safe', a); }"))
        .isEmpty();
  }

  /** Scenario: Member read. The path starts at the member read. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT", "JAVASCRIPT"})
  void memberReadStartsThePath(final AnalysisLanguage language) {
    final List<TaintFinding> found =
        run(language, "function run() { const q = req.query; db.sink(q); }");
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
    final String body = "function run() { const a = source(); const b = 'x' + a; db.sink(b); }";
    assertThat(run(language, body)).isEqualTo(run(language, body));
  }

  /** Scenario: Annotated parameter (TypeScript decorators). The path starts at the parameter. */
  @ParameterizedTest
  @EnumSource(
      value = AnalysisLanguage.class,
      names = {"TYPESCRIPT"})
  void annotatedParameterStartsThePath(final AnalysisLanguage language) {
    final String source =
        "class Handler {\n  handle(@Untrusted input: string) {\n    db.sink(input);\n  }\n}\n";
    final List<TaintFinding> found =
        TaintAnalysis.run(NodeTaintFixtures.graph(language, source, "handle"), SPEC);
    assertThat(found).hasSize(1);
    assertThat(NodeTaintFixtures.messages(found.get(0).path()))
        .containsExactly("source: Untrusted", "sink: sink argument 0");
  }

  private static List<TaintFinding> run(final AnalysisLanguage language, final String source) {
    return TaintAnalysis.run(NodeTaintFixtures.graph(language, source, "run"), SPEC);
  }
}
