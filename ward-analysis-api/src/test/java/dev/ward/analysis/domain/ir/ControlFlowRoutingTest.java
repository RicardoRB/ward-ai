package dev.ward.analysis.domain.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks how jumps and exceptions are routed through the frames of a body. */
class ControlFlowRoutingTest {

  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);
  private static final int OUTER_HANDLER = 2;
  private static final int INNER_FINALLY = 3;
  private static final int LOOP_FINALLY = 4;

  @Test
  void callOutsideAnyTryAddsNoExceptionEdge() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();
    builder.add(node("call"), true);

    assertThat(ControlFlowDump.render(builder.build("m", SPAN)))
        .isEqualTo("graph m\nB0 [call@1] -> B1\nB1 [exit]\n");
  }

  @Test
  void exceptionThroughAnInnerFinallyReachesTheOuterHandler() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();
    builder.tries().pushTry(true, false);
    builder.tries().pushTry(false, true);
    builder.add(node("call"), true);
    builder.tries().endTryBody();
    builder.tries().beginFinally();
    builder.add(node("cleanup"), false);
    builder.tries().popTry();
    builder.tries().endTryBody();
    builder.tries().beginCatch();
    builder.tries().endCatch();
    builder.tries().beginFinally();
    builder.tries().popTry();
    final ControlFlowGraph graph = builder.build("m", SPAN);

    assertThat(hasEdge(graph, 0, INNER_FINALLY, FlowEdgeKind.EXCEPTION)).isTrue();
    assertThat(hasEdge(graph, INNER_FINALLY, OUTER_HANDLER, FlowEdgeKind.EXCEPTION)).isTrue();
  }

  @Test
  void breakOutOfALoopRunsTheFinallyBlockOnTheWay() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();
    final int after = builder.newBlock();
    final int header = builder.newBlock();
    builder.edge(builder.current(), header, FlowEdgeKind.NORMAL);
    builder.moveTo(header);
    builder.jumps().pushLoop(null, after, header);
    builder.tries().pushTry(false, true);
    builder.jumps().breakTo(null);
    builder.tries().endTryBody();
    builder.tries().beginFinally();
    builder.tries().popTry();
    builder.jumps().popFrame();
    builder.moveTo(after);
    final ControlFlowGraph graph = builder.build("m", SPAN);

    assertThat(hasEdge(graph, header, LOOP_FINALLY, FlowEdgeKind.NORMAL)).isTrue();
    assertThat(hasEdge(graph, LOOP_FINALLY, after, FlowEdgeKind.NORMAL)).isTrue();
  }

  @Test
  void yieldLeavesTheInnermostSwitch() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();
    final int after = builder.newBlock();
    builder.jumps().pushSwitch(after);
    builder.add(node("yield"), false);
    builder.jumps().yieldTo();
    builder.jumps().popFrame();
    builder.moveTo(after);
    final ControlFlowGraph graph = builder.build("m", SPAN);

    assertThat(hasEdge(graph, 0, after, FlowEdgeKind.NORMAL)).isTrue();
  }

  @Test
  void continueAndBreakOnlyTakeTheTargetsTheyNameLoopsAndLabels() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();
    final int after = builder.newBlock();
    builder.jumps().pushLabel("block", after);

    assertThatThrownBy(() -> builder.jumps().continueTo("block"))
        .isInstanceOf(IllegalStateException.class);
    builder.jumps().breakTo("block");
    builder.jumps().popFrame();
    final ControlFlowGraph graph = builder.build("m", SPAN);

    assertThat(hasEdge(graph, 0, after, FlowEdgeKind.NORMAL)).isTrue();
  }

  @Test
  void continueWithoutAnyLoopIsRejected() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();

    assertThatThrownBy(() -> builder.jumps().continueTo(null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void exceptionInsideACatchWithoutFinallyHasNoHandlerOfItsOwn() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();
    builder.tries().pushTry(true, false);
    builder.tries().endTryBody();
    builder.tries().beginCatch();
    final int catchBlock = builder.current();
    builder.add(node("call"), true);
    builder.tries().endCatch();
    builder.tries().beginFinally();
    builder.tries().popTry();
    final ControlFlowGraph graph = builder.build("m", SPAN);

    assertThat(graph.blocks().get(catchBlock).successors())
        .noneMatch(edge -> edge.kind() == FlowEdgeKind.EXCEPTION);
  }

  @Test
  void graphWithAnEntryOutsideItsBlocksIsRejected() {
    final List<BasicBlock> blocks =
        List.of(new BasicBlock(0, List.of(), List.of()), new BasicBlock(1, List.of(), List.of()));

    assertThatThrownBy(() -> new ControlFlowGraph("m", SPAN, blocks, -1, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static SyntaxNode node(final String kind) {
    return new SyntaxNode(kind, SPAN, Map.of(), List.of());
  }

  private static boolean hasEdge(
      final ControlFlowGraph graph, final int from, final int to, final FlowEdgeKind kind) {
    return graph.blocks().get(from).successors().contains(new FlowEdge(to, kind));
  }
}
