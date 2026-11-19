package dev.ward.analysis.domain.rust;

import static dev.ward.analysis.domain.rust.RustFlowFixtures.edges;
import static dev.ward.analysis.domain.rust.RustFlowFixtures.graph;
import static dev.ward.analysis.domain.rust.RustFlowFixtures.hasBackEdge;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import org.junit.jupiter.api.Test;

class RustControlFlowLoopTest {
  private static final String NO_PARAMETER = "f(0)";
  private static final String ONE_PARAMETER = "f(1)";

  @Test
  void aLoopHasABackEdgeAndABreakLeavesIt() {
    final ControlFlowGraph graph =
        graph(
            "fn f() -> u8 { let mut n = 0; loop { n += 1; if n > 3 { break; } } n }",
            NO_PARAMETER
        );

    assertThat(hasBackEdge(graph)).isTrue();
    assertThat(RustFlowFixtures.accesses(graph)).anyMatch(
        access -> access.kind() == AccessKind.WRITE
    );
  }

  @Test
  void aWhileLoopTestsBeforeEachRound() {
    final ControlFlowGraph graph =
        graph("fn f(mut n: u8) { while n > 0 { n -= 1; } }", ONE_PARAMETER);

    assertThat(hasBackEdge(graph)).isTrue();
    assertThat(edges(graph, FlowEdgeKind.FALSE)).isEqualTo(1);
  }

  @Test
  void aForLoopBindsItsVariableFromTheIterable() {
    final ControlFlowGraph graph =
        graph("fn f(items: Vec<u8>) { for item in items { let _ = item; } }", ONE_PARAMETER);
    final List<String> reads =
        RustFlowFixtures.accesses(graph).stream()
            .filter(access -> access.kind() == AccessKind.READ)
            .map(VariableAccess::name)
            .toList();

    assertThat(hasBackEdge(graph)).isTrue();
    assertThat(reads).contains("items", "item");
  }

  @Test
  void aLabeledBreakLeavesTheNamedLoopAndContinueRestartsIt() {
    final ControlFlowGraph graph =
        graph(
            "fn f() { 'outer: loop { loop { break 'outer; } } 'next: for i in 0..2 { continue 'next; } }",
            NO_PARAMETER
        );

    assertThat(hasBackEdge(graph)).isTrue();
    assertThat(RustFlowFixtures.reachesExit(graph, graph.entry())).isFalse();
  }

  @Test
  void aLabeledBlockIsLeftByItsBreak() {
    final ControlFlowGraph graph =
        graph("fn f(c: bool) -> u8 { let v = 'a: { if c { break 'a 1; } 2 }; v }", ONE_PARAMETER);

    assertThat(edges(graph, FlowEdgeKind.TRUE)).isEqualTo(1);
  }

  @Test
  void aReturnLeavesTheBodyAndTheCodeAfterItIsUnreachable() {
    final ControlFlowGraph graph = graph("fn f() -> u8 { return 1; let dead = 2; }", NO_PARAMETER);
    final BasicBlock first = graph.blocks().get(graph.entry());
    final BasicBlock deadBlock =
        graph.blocks().stream()
            .filter(block -> block.accesses().stream().anyMatch(a -> "dead".equals(a.name())))
            .findFirst()
            .orElseThrow();

    assertThat(RustFlowFixtures.reachesExit(graph, first.id())).isTrue();
    assertThat(RustFlowFixtures.reachable(graph)).doesNotContain(deadBlock.id());
  }
}
