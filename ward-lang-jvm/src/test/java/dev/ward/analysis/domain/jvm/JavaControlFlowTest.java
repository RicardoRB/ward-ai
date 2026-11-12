package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowDump;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.port.ControlFlowResult;
import org.junit.jupiter.api.Test;

class JavaControlFlowTest {

  @Test
  void emptyMethodHasOnlyAnEdgeFromEntryToExit() {
    final ControlFlowGraph graph = lowerNamed("class C { void run() { } }", "C#run(0)");

    assertThat(ControlFlowDump.render(graph))
        .isEqualTo("graph C#run(0)\nB0 [entry] -> B1\nB1 [exit]\n");
  }

  @Test
  void ifWithoutElseBranchesOnItsOutcome() {
    final String source = "class C { void run(boolean a) { if (a) { x(); } y(); } }";
    final ControlFlowGraph graph = lowerNamed(source, "C#run(1)");

    final int condition = JavaFlowFixtures.blockWith(graph, source, "a");
    assertThat(
            JavaFlowFixtures.hasEdge(
                graph,
                condition,
                JavaFlowFixtures.blockWith(graph, source, "x()"),
                FlowEdgeKind.TRUE))
        .isTrue();
    assertThat(
            JavaFlowFixtures.hasEdge(
                graph,
                condition,
                JavaFlowFixtures.blockWith(graph, source, "y()"),
                FlowEdgeKind.FALSE))
        .isTrue();
  }

  @Test
  void shortCircuitOperandIsReachedOnlyThroughTheTrueEdge() {
    final String source = "class C { void run(boolean a) { a && b(); } }";
    final ControlFlowGraph graph = lowerNamed(source, "C#run(1)");

    final int operand = JavaFlowFixtures.blockWith(graph, source, "b()");
    final int first = JavaFlowFixtures.blockWith(graph, source, "a");
    assertThat(JavaFlowFixtures.hasEdge(graph, first, operand, FlowEdgeKind.TRUE)).isTrue();
    assertThat(JavaFlowFixtures.incoming(graph, operand)).containsExactly(first);
  }

  @Test
  void labelledContinueJumpsToTheIterationStepOfTheNamedLoop() {
    final String source =
        "class C { void run(int n) { outer: for (int i = 0; i < n; i++) {"
            + " for (int j = 0; j < n; j++) { continue outer; } } } }";
    final ControlFlowGraph graph = lowerNamed(source, "C#run(1)");

    final int jump = JavaFlowFixtures.blockWith(graph, source, "continue outer");
    assertThat(
            JavaFlowFixtures.hasEdge(
                graph, jump, JavaFlowFixtures.blockWith(graph, source, "i++"), FlowEdgeKind.NORMAL))
        .isTrue();
  }

  @Test
  void switchCaseWithoutBreakFallsThroughToTheNextCase() {
    final String source =
        "class C { void run(int n) { switch (n) { case 1: one(); case 2: two(); break; } } }";
    final ControlFlowGraph graph = lowerNamed(source, "C#run(1)");

    assertThat(
            JavaFlowFixtures.hasEdge(
                graph,
                JavaFlowFixtures.blockWith(graph, source, "one()"),
                JavaFlowFixtures.blockWith(graph, source, "two()"),
                FlowEdgeKind.NORMAL))
        .isTrue();
  }

  @Test
  void returnInsideTryPassesThroughTheFinallyBlock() {
    final String source =
        "class C { int run(boolean a) { try { if (a) { return 1; } } finally { cleanup(); } return 2; } }";
    final ControlFlowGraph graph = lowerNamed(source, "C#run(1)");

    final int returning = JavaFlowFixtures.blockWith(graph, source, "return 1");
    final int finallyBlock = JavaFlowFixtures.blockWith(graph, source, "cleanup()");
    assertThat(JavaFlowFixtures.hasEdge(graph, returning, finallyBlock, FlowEdgeKind.NORMAL))
        .isTrue();
    assertThat(JavaFlowFixtures.successorsOf(graph, returning)).doesNotContain(graph.exit());
  }

  @Test
  void throwOutsideAnyTryHasAnExceptionEdgeToTheExit() {
    final String source =
        "class C { void run(boolean a) { if (a) { throw new RuntimeException(); } } }";
    final ControlFlowGraph graph = lowerNamed(source, "C#run(1)");

    final int throwing = JavaFlowFixtures.blockWith(graph, source, "throw");
    assertThat(JavaFlowFixtures.hasEdge(graph, throwing, graph.exit(), FlowEdgeKind.EXCEPTION))
        .isTrue();
  }

  @Test
  void jumpsWithoutATargetLeaveTheBodyAndSiblingsStillLower() {
    final ControlFlowResult result =
        JavaFlowFixtures.lower(
            "class C { void bad() { break; } void cont() { continue; }"
                + " void lbl() { while (true) { break nowhere; } } void good() { x(); } }");

    assertThat(result.failures()).isEmpty();
    assertThat(JavaFlowFixtures.names(result))
        .contains("C#bad(0)", "C#cont(0)", "C#lbl(0)", "C#good(0)");
  }

  @Test
  void loweringTheSameSourceTwiceGivesTheSameDump() {
    final String source = "class C { void run(int n) { for (int i = 0; i < n; i++) { w(i); } } }";

    assertThat(JavaFlowFixtures.dumps(JavaFlowFixtures.lower(source)))
        .isEqualTo(JavaFlowFixtures.dumps(JavaFlowFixtures.lower(source)));
  }

  private static ControlFlowGraph lowerNamed(final String source, final String name) {
    final ControlFlowResult result = JavaFlowFixtures.lower(source);
    return result.graphs().stream()
        .filter(graph -> graph.body().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no graph named " + name));
  }
}
