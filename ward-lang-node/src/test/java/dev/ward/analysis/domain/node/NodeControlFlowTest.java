package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.port.ControlFlowResult;
import org.junit.jupiter.api.Test;

class NodeControlFlowTest {

  @Test
  void nestedArrowFunctionGetsItsOwnGraph() {
    final ControlFlowResult result =
        NodeFlowFixtures.lower(
            "function outer() {\n  const inner = () => {\n    x();\n  };\n  inner();\n}\n");

    assertThat(NodeFlowFixtures.names(result)).containsExactlyInAnyOrder("outer", "inner");
  }

  @Test
  void awaitIsSequentialFlowInsideItsFunction() {
    final String source = "async function f() {\n  const a = await h();\n  g(a);\n}\n";
    final ControlFlowGraph graph = NodeFlowFixtures.lowerNamed(source, "f");

    assertThat(
            NodeFlowFixtures.hasEdge(
                graph,
                NodeFlowFixtures.blockWith(graph, source, "await h()"),
                NodeFlowFixtures.blockWith(graph, source, "g(a)"),
                FlowEdgeKind.NORMAL))
        .isTrue();
  }

  @Test
  void shortCircuitOperandIsReachedOnlyThroughTheTrueEdge() {
    final String source = "function f(a) {\n  a && b();\n}\n";
    final ControlFlowGraph graph = NodeFlowFixtures.lowerNamed(source, "f");

    final int first = NodeFlowFixtures.blockWith(graph, source, "a");
    assertThat(
            NodeFlowFixtures.hasEdge(
                graph, first, NodeFlowFixtures.blockWith(graph, source, "b()"), FlowEdgeKind.TRUE))
        .isTrue();
  }

  @Test
  void throwOutsideAnyTryHasAnExceptionEdgeToTheExit() {
    final String source = "function f(a) {\n  if (a) {\n    throw new Error();\n  }\n}\n";
    final ControlFlowGraph graph = NodeFlowFixtures.lowerNamed(source, "f");

    assertThat(
            NodeFlowFixtures.hasEdge(
                graph,
                NodeFlowFixtures.blockWith(graph, source, "throw"),
                graph.exit(),
                FlowEdgeKind.EXCEPTION))
        .isTrue();
  }

  @Test
  void returnInsideTryPassesThroughTheFinallyBlock() {
    final String source =
        "function f(a) {\n  try {\n    if (a) {\n      return 1;\n    }\n  } finally {\n    cleanup();\n  }\n}\n";
    final ControlFlowGraph graph = NodeFlowFixtures.lowerNamed(source, "f");

    assertThat(
            NodeFlowFixtures.hasEdge(
                graph,
                NodeFlowFixtures.blockWith(graph, source, "return 1"),
                NodeFlowFixtures.blockWith(graph, source, "cleanup()"),
                FlowEdgeKind.NORMAL))
        .isTrue();
  }

  @Test
  void topLevelStatementsOfAModuleAreTheModuleGraph() {
    final ControlFlowResult result = NodeFlowFixtures.lower("first();\nsecond();\n");

    assertThat(NodeFlowFixtures.names(result)).containsExactly("<module>");
  }

  @Test
  void bodyWithAnUnsupportedJumpFailsAndItsSiblingsStillLower() {
    final ControlFlowResult result =
        NodeFlowFixtures.lower("function bad() { break; }\nfunction good() { x(); }\n");

    assertThat(result.failures()).hasSize(1);
    assertThat(result.failures().get(0).message()).contains("bad");
    assertThat(NodeFlowFixtures.names(result)).containsExactly("good");
  }

  @Test
  void loweringTheSameSourceTwiceGivesTheSameDump() {
    final String source = "function f(n) {\n  for (let i = 0; i < n; i++) { w(i); }\n}\n";

    assertThat(NodeFlowFixtures.dumps(NodeFlowFixtures.lower(source)))
        .isEqualTo(NodeFlowFixtures.dumps(NodeFlowFixtures.lower(source)));
  }
}
