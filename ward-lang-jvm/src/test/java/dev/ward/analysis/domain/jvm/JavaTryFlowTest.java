package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ControlFlowResult;
import org.junit.jupiter.api.Test;

/**
 * Checks that the code after a try statement is reached from the try's finally and catch blocks.
 */
class JavaTryFlowTest {

  @Test
  void statementAfterTryFinallyIsReachableFromTheFinallyBlock() {
    final String source =
        "class C { void run() { try { work(); } finally { cleanup(); } done(); } }";
    final ControlFlowGraph graph = lowerNamed(source, "C#run(0)");

    final int after = JavaFlowFixtures.blockWith(graph, source, "done()");
    assertThat(
            JavaFlowFixtures.reachable(
                graph, JavaFlowFixtures.blockWith(graph, source, "cleanup()"), after))
        .isTrue();
    assertThat(
            JavaFlowFixtures.reachable(
                graph, JavaFlowFixtures.blockWith(graph, source, "work()"), after))
        .isTrue();
  }

  @Test
  void statementAfterTryCatchFinallyIsReachableFromTheCatchAndFinallyBlocks() {
    final String source =
        "class C { void run() { try { risky(); } catch (RuntimeException e) { recover(); }"
            + " finally { cleanup(); } done(); } }";
    final ControlFlowGraph graph = lowerNamed(source, "C#run(0)");

    final int after = JavaFlowFixtures.blockWith(graph, source, "done()");
    assertThat(
            JavaFlowFixtures.reachable(
                graph, JavaFlowFixtures.blockWith(graph, source, "recover()"), after))
        .isTrue();
    assertThat(
            JavaFlowFixtures.reachable(
                graph, JavaFlowFixtures.blockWith(graph, source, "cleanup()"), after))
        .isTrue();
  }

  @Test
  void statementAfterReturnInsideTryIsStillReachableFromTheBody() {
    final String source =
        "class C { int run(boolean a) { try { if (a) { return 1; } work(); } finally { cleanup(); }"
            + " return 2; } }";
    final ControlFlowGraph graph = lowerNamed(source, "C#run(1)");

    assertThat(
            JavaFlowFixtures.reachable(
                graph,
                JavaFlowFixtures.blockWith(graph, source, "work()"),
                JavaFlowFixtures.blockWith(graph, source, "return 2")))
        .isTrue();
  }

  @Test
  void statementAfterTryFinallyStartsItsOwnBlockReachedByANormalEdgeFromTheFinally() {
    final String source =
        "class C { void run() { try { work(); } finally { cleanup(); } done(); } }";
    final ControlFlowGraph graph = lowerNamed(source, "C#run(0)");

    final int finallyBlock = JavaFlowFixtures.blockWith(graph, source, "cleanup()");
    final int after = JavaFlowFixtures.blockWith(graph, source, "done()");
    assertThat(after).isNotEqualTo(finallyBlock);
    assertThat(JavaFlowFixtures.hasEdge(graph, finallyBlock, after, FlowEdgeKind.NORMAL)).isTrue();
  }

  @Test
  void exceptionFromATryWithResourcesBodyLeavesTheMethod() {
    final String source =
        "class C { void run(java.io.Reader in) throws Exception { try (java.io.Reader r = open(in)) {"
            + " read(r); } done(); } }";
    final ControlFlowGraph graph = lowerNamed(source, "C#run(1)");

    final int body = JavaFlowFixtures.blockWith(graph, source, "read(r)");
    assertThat(JavaFlowFixtures.hasEdge(graph, body, graph.exit(), FlowEdgeKind.EXCEPTION))
        .isTrue();
  }

  @Test
  void forEachBindsTheLoopVariableAtTheStartOfTheBody() {
    final String source =
        "class C { void run(java.util.List<String> items) { for (String item : items) {"
            + " work(item); } } }";
    final ControlFlowGraph graph = lowerNamed(source, "C#run(1)");

    final int body = JavaFlowFixtures.blockWith(graph, source, "work(item)");
    final SyntaxNode first = graph.blocks().get(body).nodes().get(0);
    assertThat(source.substring(first.span().startOffset(), first.span().endOffset()))
        .isEqualTo("item");
  }

  private static ControlFlowGraph lowerNamed(final String source, final String name) {
    final ControlFlowResult result = JavaFlowFixtures.lower(source);
    return result.graphs().stream()
        .filter(graph -> graph.body().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no graph named " + name));
  }
}
