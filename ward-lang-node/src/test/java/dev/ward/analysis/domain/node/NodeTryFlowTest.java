package dev.ward.analysis.domain.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/**
 * Checks that the code after a try statement is reached from the try's finally and catch blocks.
 */
class NodeTryFlowTest {

  @Test
  void statementAfterTryCatchFinallyIsReachableFromTheCatchAndFinallyBlocks() {
    final String source =
        "function f() {\n  try {\n    risky();\n  } catch (e) {\n    recover();\n  } finally {\n"
            + "    cleanup();\n  }\n  done();\n}\n";
    final ControlFlowGraph graph = NodeFlowFixtures.lowerNamed(source, "f");

    final int after = NodeFlowFixtures.blockWith(graph, source, "done()");
    assertThat(
            NodeFlowFixtures.reachable(
                graph, NodeFlowFixtures.blockWith(graph, source, "recover()"), after))
        .isTrue();
    assertThat(
            NodeFlowFixtures.reachable(
                graph, NodeFlowFixtures.blockWith(graph, source, "cleanup()"), after))
        .isTrue();
  }

  @Test
  void statementAfterReturnInsideTryIsStillReachableFromTheBody() {
    final String source =
        "function f(a) {\n  try {\n    if (a) {\n      return 1;\n    }\n    work();\n"
            + "  } finally {\n    cleanup();\n  }\n  return 2;\n}\n";
    final ControlFlowGraph graph = NodeFlowFixtures.lowerNamed(source, "f");

    assertThat(
            NodeFlowFixtures.reachable(
                graph,
                NodeFlowFixtures.blockWith(graph, source, "work()"),
                NodeFlowFixtures.blockWith(graph, source, "return 2")))
        .isTrue();
  }

  @Test
  void statementAfterTryFinallyStartsItsOwnBlockReachedByANormalEdgeFromTheFinally() {
    final String source =
        "function f() {\n  try {\n    work();\n  } finally {\n    cleanup();\n  }\n  done();\n}\n";
    final ControlFlowGraph graph = NodeFlowFixtures.lowerNamed(source, "f");

    final int finallyBlock = NodeFlowFixtures.blockWith(graph, source, "cleanup()");
    final int after = NodeFlowFixtures.blockWith(graph, source, "done()");
    assertThat(after).isNotEqualTo(finallyBlock);
    assertThat(NodeFlowFixtures.hasEdge(graph, finallyBlock, after, FlowEdgeKind.NORMAL)).isTrue();
  }

  @Test
  void forOfBindsTheLoopVariableAtTheStartOfTheBody() {
    final String source =
        "function run(items) {\n  for (const item of items) {\n    work(item);\n  }\n}\n";
    final ControlFlowGraph graph = NodeFlowFixtures.lowerNamed(source, "run");

    final int body = NodeFlowFixtures.blockWith(graph, source, "work(item)");
    final SyntaxNode first = graph.blocks().get(body).nodes().get(0);
    assertThat(source.substring(first.span().startOffset(), first.span().endOffset()))
        .contains("item");
  }
}
