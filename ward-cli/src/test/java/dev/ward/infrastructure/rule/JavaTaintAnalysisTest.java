package dev.ward.infrastructure.rule;

import static dev.ward.infrastructure.rule.JavaTaintFixtures.messages;
import static dev.ward.infrastructure.rule.JavaTaintFixtures.run;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.flow.taint.TaintFinding;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the taint analysis on lowered Java methods with a fake specification. */
class JavaTaintAnalysisTest {

  /** Scenario: Direct flow. The source call's value reaches the sink in two steps. */
  @Test
  void directFlowIsOneFindingWithTwoSteps() {
    final List<TaintFinding> found = run("String id = source(); sink(id);");
    assertThat(found).hasSize(1);
    assertThat(messages(found.get(0).path()))
        .containsExactly("source: source", "sink: sink argument 0");
  }

  /** Scenario: Flow through assignments. The path is source, the assignment of b, and the sink. */
  @Test
  void flowThroughAssignmentsHasThreeSteps() {
    final List<TaintFinding> found = run("String a = source(); String b = \"x\" + a; sink(b);");
    assertThat(found).hasSize(1);
    assertThat(messages(found.get(0).path()))
        .containsExactly("source: source", "assigned to b", "sink: sink argument 0");
  }

  /** Scenario: Overwritten before the sink. The overwriting write reaches the sink instead. */
  @Test
  void overwrittenBeforeSinkIsNotReported() {
    assertThat(run("String v = source(); v = \"safe\"; sink(v);")).isEmpty();
  }

  /** Scenario: Sanitized value. The sanitizer's result is clean. */
  @Test
  void sanitizedValueIsNotReported() {
    assertThat(run("String a = source(); String b = clean(a); sink(b);")).isEmpty();
  }

  /** Scenario: Sanitized on one branch only. The unsanitized branch still reaches the sink. */
  @Test
  void sanitizedOnOneBranchIsReportedThroughTheOtherBranch() {
    final List<TaintFinding> found =
        run("String a = source(); String b = a;" + " if (a.isEmpty()) { b = clean(a); } sink(b);");
    assertThat(found).hasSize(1);
    assertThat(messages(found.get(0).path()))
        .containsExactly("source: source", "assigned to b", "sink: sink argument 0");
  }

  /** Scenario: Unguarded argument. The sink guards argument 0 only. */
  @Test
  void taintInAnUnguardedArgumentIsNotReported() {
    assertThat(run("String a = source(); sink(\"safe\", a);")).isEmpty();
  }

  /** Scenario: Annotated parameter. The path starts at the parameter. */
  @Test
  void annotatedParameterStartsThePath() {
    final List<TaintFinding> found = run("String p = q; sink(p);", "void run(@Untrusted String q)");
    assertThat(found).hasSize(1);
    assertThat(messages(found.get(0).path()).get(0)).isEqualTo("source: Untrusted");
  }

  /** Scenario: Repeated run. The same source gives the same findings and paths. */
  @Test
  void repeatedRunsGiveIdenticalPaths() {
    final String body = "String a = source(); String b = \"x\" + a; sink(b);";
    assertThat(run(body)).isEqualTo(run(body));
  }

  /** A receiver with a known type matches a sink entry that names it. */
  @Test
  void receiverTypeMatchesTheSinkEntry() {
    final List<TaintFinding> found =
        run("java.sql.Statement st = null; String a = source(); st.executeQuery(a);", "void run()");
    assertThat(found).hasSize(1);
    assertThat(found.get(0).sink().name()).isEqualTo("executeQuery");
  }
}
