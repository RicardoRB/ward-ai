package dev.ward.analysis.domain.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.port.ControlFlowResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ControlFlowModelTest {

  private static final Span SPAN = new Span(1, 1, 1, 2, 0, 1);

  @Test
  void blockCopiesItsListsSoLaterChangesDoNotShowThrough() {
    final List<SyntaxNode> nodes = new ArrayList<>(List.of(node()));
    final List<FlowEdge> edges = new ArrayList<>(List.of(new FlowEdge(1, FlowEdgeKind.NORMAL)));
    final BasicBlock block = new BasicBlock(0, nodes, edges);
    nodes.clear();
    edges.clear();

    assertThat(block.nodes()).hasSize(1);
    assertThat(block.successors()).containsExactly(new FlowEdge(1, FlowEdgeKind.NORMAL));
  }

  @Test
  void blockRejectsNullListsNullElementsAndNegativeIds() {
    assertThatThrownBy(() -> new BasicBlock(0, null, List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new BasicBlock(0, List.of(), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new BasicBlock(0, Arrays.asList((SyntaxNode) null), List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new BasicBlock(-1, List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void edgeRejectsNegativeTargetAndMissingKind() {
    assertThatThrownBy(() -> new FlowEdge(-1, FlowEdgeKind.NORMAL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FlowEdge(0, null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void graphCopiesBlocksAndNeedsDistinctEntryAndExitBlocks() {
    final List<BasicBlock> blocks = new ArrayList<>(twoBlocks());
    final ControlFlowGraph graph = new ControlFlowGraph("App#run(0)", SPAN, blocks, 0, 1);
    blocks.clear();

    assertThat(graph.blocks()).hasSize(2);
    assertThatThrownBy(() -> new ControlFlowGraph("App#run(0)", SPAN, twoBlocks(), 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ControlFlowGraph("App#run(0)", SPAN, twoBlocks(), 0, 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ControlFlowGraph(null, SPAN, twoBlocks(), 0, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ControlFlowGraph("App#run(0)", null, twoBlocks(), 0, 1))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void resultCopiesItsListsAndRejectsNulls() {
    final List<ControlFlowGraph> graphs = new ArrayList<>();
    final ControlFlowResult result = new ControlFlowResult(graphs, List.of());
    graphs.add(new ControlFlowGraph("x", SPAN, twoBlocks(), 0, 1));

    assertThat(result.graphs()).isEmpty();
    assertThatThrownBy(() -> new ControlFlowResult(null, List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ControlFlowResult(List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }

  private static List<BasicBlock> twoBlocks() {
    return List.of(
        new BasicBlock(0, List.of(), List.of()), new BasicBlock(1, List.of(), List.of()));
  }

  private static SyntaxNode node() {
    return new SyntaxNode("statement", SPAN, Map.of(), List.of());
  }
}
