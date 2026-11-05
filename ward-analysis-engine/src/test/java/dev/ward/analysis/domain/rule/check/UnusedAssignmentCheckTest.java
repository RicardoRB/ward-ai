package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.flow.GraphFixtures;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The unused-assignment rule on hand-built graphs, one case per scenario of the spec. */
class UnusedAssignmentCheckTest {

  private static final int THIRD_LINE = 3;

  private static final String MESSAGE = "the value assigned to '{value}' is never read";
  private static final SyntaxNode ROOT =
      new SyntaxNode("file", new Span(1, 1, 1, 2, 0, 1), Map.of(), List.of());

  @Test
  void overwrittenInitializerIsReportedAndTheLaterUseIsNot() {
    // int x = compute(); x = 2; use(x);
    final ControlFlowGraph graph =
        GraphFixtures.graph(
            GraphFixtures.empty().write("x", 1).write("x", 2).read("x", THIRD_LINE).edge(1),
            GraphFixtures.empty());

    assertThat(CheckResults.messages(graph))
        .containsExactly("the value assigned to 'x' is never read");
    assertThat(CheckResults.lines(graph)).containsExactly(1);
  }

  @Test
  void aValueThatIsNeverReadIsReported() {
    // int f(int total) { int result = total; } with result not read afterwards
    final ControlFlowGraph graph =
        GraphFixtures.graph(
            GraphFixtures.empty().parameter("total", 1).read("total", 2).write("result", 2).edge(1),
            GraphFixtures.empty());

    assertThat(CheckResults.messages(graph))
        .containsExactly("the value assigned to 'result' is never read");
  }

  @Test
  void aVariableCapturedByANestedBodyIsNeverReported() {
    // int x = 1; Runnable r = () -> use(x);
    final ControlFlowGraph graph =
        GraphFixtures.graph(
            GraphFixtures.empty().write("x", 1).capture("x", 2).edge(1), GraphFixtures.empty());

    assertThat(CheckResults.messages(graph)).isEmpty();
  }

  @Test
  void theFinalAssignmentToAnIgnoredNameIsNotReported() {
    // int ignoredY = 1; ignoredY = 2; (no read after)
    final ControlFlowGraph graph =
        GraphFixtures.graph(
            GraphFixtures.empty().write("ignoredY", 1).write("ignoredY", 2).edge(1),
            GraphFixtures.empty());

    assertThat(CheckResults.messages(graph))
        .containsExactly("the value assigned to 'ignoredY' is never read");
    assertThat(CheckResults.lines(graph)).containsExactly(1);
  }

  @Test
  void anUnusedNameWithOneValueIsNotReported() {
    // int unusedValue = compute(); (no other write, no read)
    final ControlFlowGraph graph =
        GraphFixtures.graph(
            GraphFixtures.empty().write("unusedValue", 1).edge(1), GraphFixtures.empty());

    assertThat(CheckResults.messages(graph)).isEmpty();
  }

  @Test
  void anIncomingParameterValueThatIsNeverOverwrittenIsNotReported() {
    // int f(int a) { return 2; }
    final ControlFlowGraph graph =
        GraphFixtures.graph(GraphFixtures.empty().parameter("a", 1).edge(1), GraphFixtures.empty());

    assertThat(CheckResults.messages(graph)).isEmpty();
  }

  @Test
  void anIncomingParameterValueOverwrittenBeforeItsReadIsReported() {
    // int f(int a) { a = THIRD_LINE; return a; }: PMD reports the initial value, as on
    // Parameters.java
    final ControlFlowGraph graph =
        GraphFixtures.graph(
            GraphFixtures.empty().parameter("a", 1).write("a", 2).read("a", THIRD_LINE).edge(1),
            GraphFixtures.empty());

    assertThat(CheckResults.messages(graph))
        .containsExactly("the value assigned to 'a' is never read");
    assertThat(CheckResults.lines(graph)).containsExactly(1);
  }

  @Test
  void anIncomingParameterValueThatIsOverwrittenWithoutARead() {
    // int f(int a) { a = THIRD_LINE; return 2; }
    final ControlFlowGraph graph =
        GraphFixtures.graph(
            GraphFixtures.empty().parameter("a", 1).write("a", 2).edge(1), GraphFixtures.empty());

    assertThat(CheckResults.messages(graph))
        .containsExactly(
            "the value assigned to 'a' is never read", "the value assigned to 'a' is never read");
  }

  @Test
  void theRuleNeedsItsFlowGraphs() {
    final UnusedAssignmentCheck check = new UnusedAssignmentCheck(MESSAGE);

    assertThat(check.matches(ROOT, FlowView.NONE)).isEmpty();
    assertThatThrownBy(() -> check.matches(ROOT)).isInstanceOf(AnalysisFailedException.class);
  }
}
