package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The unreachable-code check on hand-built graphs: entry 0 and exit 1, then the other blocks. */
class UnreachableCodeCheckTest {

  private static final String MESSAGE = "unreachable code";
  private static final int LINE_WIDTH = 10;
  private static final int THIRD_LINE = 3;
  private static final SyntaxNode ROOT =
      new SyntaxNode("file", new Span(1, 1, 1, 1, 0, 0), Map.of(), List.of());

  private static SyntaxNode statement(final int line) {
    return new SyntaxNode(
        "statement",
        new Span(line, 1, line, 2, line * LINE_WIDTH, line * LINE_WIDTH + 1),
        Map.of(),
        List.of());
  }

  private static BasicBlock block(final int id, final SyntaxNode node, final int... targets) {
    final List<FlowEdge> edges =
        java.util.Arrays.stream(targets)
            .mapToObj(target -> new FlowEdge(target, FlowEdgeKind.NORMAL))
            .toList();
    return new BasicBlock(id, node == null ? List.of() : List.of(node), edges, List.of());
  }

  private static ControlFlowGraph graph(final BasicBlock... blocks) {
    return new ControlFlowGraph("g", new Span(1, 1, 1, 1, 0, 0), List.of(blocks), 0, 1);
  }

  private static List<RuleMatch> check(final ControlFlowGraph graph) {
    return new UnreachableCodeCheck(MESSAGE).matches(ROOT, new FlowView(List.of(graph)));
  }

  @Test
  void aReachableGraphHasNoFindings() {
    final ControlFlowGraph graph =
        graph(block(0, statement(1), 2), block(1, null), block(2, statement(2), 1));

    assertThat(check(graph)).isEmpty();
  }

  @Test
  void theFirstBlockOfADeadRunIsReportedOnceAndItsFollowersAreNot() {
    // return; a(); b();  -- block 3 is dead and leads to the dead block 4
    final ControlFlowGraph graph =
        graph(
            block(0, statement(1), 2),
            block(1, null),
            block(2, statement(2), 1),
            block(3, statement(3), 4),
            block(4, statement(4), 1));

    final List<RuleMatch> found = check(graph);

    assertThat(found).extracting(RuleMatch::message).containsExactly(MESSAGE);
    assertThat(found).extracting(match -> match.span().startLine()).containsExactly(THIRD_LINE);
  }

  @Test
  void aDeadBlockWithoutStatementsIsNotReported() {
    final ControlFlowGraph graph =
        graph(
            block(0, statement(1), 2),
            block(1, null),
            block(2, statement(2), 1),
            block(3, null, 1));

    assertThat(check(graph)).isEmpty();
  }

  @Test
  void theCheckCannotRunWithoutFlowGraphs() {
    assertThatThrownBy(() -> new UnreachableCodeCheck(MESSAGE).matches(ROOT))
        .isInstanceOf(AnalysisFailedException.class);
  }
}
