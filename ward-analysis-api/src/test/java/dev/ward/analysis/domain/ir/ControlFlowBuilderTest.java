package dev.ward.analysis.domain.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ControlFlowBuilderTest {

  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);

  @Test
  void emptyBodyHasOnlyAnEdgeFromEntryToExit() {
    final ControlFlowGraph graph = new ControlFlowBuilder().build("m", SPAN);

    assertThat(ControlFlowDump.render(graph)).isEqualTo("graph m\nB0 [entry] -> B1\nB1 [exit]\n");
  }

  @Test
  void returnInsideTryRunsTheFinallyBlockFirst() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();
    builder.tries().pushTry(false, true);
    builder.add(node("call"), false);
    builder.tries().returnFrom();
    builder.tries().endTryBody();
    builder.tries().beginFinally();
    builder.add(node("call"), false);
    builder.tries().popTry();

    assertThat(ControlFlowDump.render(builder.build("m", SPAN)))
        .isEqualTo(
            "graph m\nB0 [call@1] -> B2\nB1 [exit]\nB2 [call@1] -> B1 B3\nB3 [empty] -> B1\n");
  }

  @Test
  void throwOutsideAnyTryHasAnExceptionEdgeToTheExit() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();
    builder.add(node("throw"), false);
    builder.tries().throwOut();

    assertThat(ControlFlowDump.render(builder.build("m", SPAN)))
        .isEqualTo("graph m\nB0 [throw@1] -> B1:exception\nB1 [exit]\n");
  }

  @Test
  void throwingStatementInsideTryHasAnExceptionEdgeToTheHandler() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();
    builder.tries().pushTry(true, false);
    builder.add(node("call"), true);
    builder.tries().endTryBody();
    builder.tries().beginCatch();
    builder.tries().endCatch();
    builder.tries().beginFinally();
    builder.tries().popTry();

    assertThat(ControlFlowDump.render(builder.build("m", SPAN)))
        .isEqualTo(
            "graph m\n"
                + "B0 [entry] -> B2:exception B3\n"
                + "B1 [exit]\n"
                + "B2 [empty] -> B4 B1:exception\n"
                + "B3 [call@1] -> B2:exception B5\n"
                + "B4 [empty] -> B5\n"
                + "B5 [empty] -> B1\n");
  }

  @Test
  void breakAndContinueUseTheirNamedLoop() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();
    final int after = builder.newBlock();
    final int header = builder.newBlock();
    builder.edge(builder.current(), header, FlowEdgeKind.NORMAL);
    builder.moveTo(header);
    builder.jumps().pushLoop("outer", after, header);
    builder.jumps().continueTo("outer");
    builder.jumps().popFrame();
    builder.moveTo(after);

    assertThat(ControlFlowDump.render(builder.build("m", SPAN)))
        .isEqualTo("graph m\nB0 [entry] -> B2\nB1 [exit]\nB2 [empty] -> B2\n");
  }

  @Test
  void jumpWithoutAnEnclosingTargetLeavesTheBodyExceptYield() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();

    builder.jumps().breakTo(null);
    builder.jumps().continueTo("nowhere");

    assertThat(ControlFlowDump.render(builder.build("m", SPAN)))
        .isEqualTo("graph m\nB0 [entry] -> B1\nB1 [exit]\n");
    assertThatThrownBy(builder.jumps()::yieldTo).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void branchAddsTrueAndFalseEdgesFromTheCurrentBlock() {
    final ControlFlowBuilder builder = new ControlFlowBuilder();
    final int yes = builder.newBlock();
    final int no = builder.newBlock();
    builder.add(node("condition"), false);
    builder.branch(yes, no);

    assertThat(builder.build("m", SPAN).blocks().get(0).successors())
        .contains(new FlowEdge(yes, FlowEdgeKind.TRUE), new FlowEdge(no, FlowEdgeKind.FALSE));
  }

  private static SyntaxNode node(final String kind) {
    return new SyntaxNode(kind, SPAN, Map.of(), List.of());
  }
}
