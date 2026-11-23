package dev.ward.analysis.domain.rust;

import static dev.ward.analysis.domain.rust.RustFlowFixtures.edges;
import static dev.ward.analysis.domain.rust.RustFlowFixtures.graph;
import static dev.ward.analysis.domain.rust.RustFlowFixtures.reachesExit;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import org.junit.jupiter.api.Test;

class RustControlFlowBranchTest {
  private static final String ONE_PARAMETER = "f(1)";
  private static final int ARMS = 3;

  @Test
  void anIfSplitsOnItsConditionAndBothBranchesJoin() {
    final ControlFlowGraph graph =
        graph(
            "fn f(c: bool) -> u8 { let mut x = 0; if c { x = 1; } else { x = 2; } x }",
            ONE_PARAMETER
        );

    assertThat(edges(graph, FlowEdgeKind.TRUE)).isEqualTo(1);
    assertThat(edges(graph, FlowEdgeKind.FALSE)).isEqualTo(1);
    assertThat(reachesExit(graph, graph.blocks().get(graph.blocks().size() - 1).id())).isTrue();
  }

  @Test
  void anIfWithAnElseIfChainsTheConditions() {
    final ControlFlowGraph graph =
        graph("fn f(a: bool, b: bool) { if a { } else if b { } else { } }", "f(2)");

    assertThat(edges(graph, FlowEdgeKind.TRUE)).isEqualTo(2);
  }

  @Test
  void aMatchGivesEachArmItsOwnBlock() {
    final ControlFlowGraph graph =
        graph("fn f(n: u8) -> u8 { match n { 0 => 1, 1 if n > 0 => 2, _ => 3 } }", ONE_PARAMETER);

    assertThat(graph.blocks().get(graph.entry()).successors()).hasSize(ARMS);
  }

  @Test
  void aMatchWithoutArmsFallsThrough() {
    final ControlFlowGraph graph = graph("fn f(n: Never) { match n {} }", ONE_PARAMETER);

    assertThat(graph.blocks().get(graph.entry()).successors()).hasSize(1);
  }

  @Test
  void aLetElseRunsItsElseBlockBeforeTheNamesExist() {
    final ControlFlowGraph graph =
        graph("fn f(o: Option<u8>) -> u8 { let Some(x) = o else { return 0 }; x }", ONE_PARAMETER);

    assertThat(edges(graph, FlowEdgeKind.TRUE)).isEqualTo(1);
    assertThat(edges(graph, FlowEdgeKind.FALSE)).isEqualTo(1);
  }

  @Test
  void aLetWithABlockInitializerLowersTheBlock() {
    final ControlFlowGraph graph =
        graph("fn f(c: bool) -> u8 { let v = if c { 1 } else { 2 }; v }", ONE_PARAMETER);

    assertThat(edges(graph, FlowEdgeKind.TRUE)).isEqualTo(1);
  }

  @Test
  void anUnsafeBlockIsPartOfTheBodyAndAClosureIsABodyOfItsOwn() {
    final String source = "fn f(a: u8) -> u8 { let g = |x: u8| x + a; unsafe { g(a) } }";
    final ControlFlowGraph graph = graph(source, ONE_PARAMETER);

    assertThat(edges(graph, FlowEdgeKind.TRUE)).isZero();
    assertThat(RustFlowFixtures.accesses(graph)).noneMatch(access -> "x".equals(access.name()));
    assertThat(RustFlowFixtures.accesses(graph)).anyMatch(
        access -> "a".equals(access.name()) && access.kind() == AccessKind.CAPTURE
    );
    assertThat(RustFlowFixtures.graphs(source)).anyMatch(
        closure -> closure.body().contains("closure@")
            && RustFlowFixtures.accesses(closure).stream().anyMatch(
                access -> "x".equals(access.name()) && access.kind() == AccessKind.READ
            )
    );
  }

  @Test
  void aQuestionMarkMayLeaveTheBodyEarly() {
    final ControlFlowGraph graph =
        graph(
            "fn f(s: &str) -> Result<u8, E> { let n = s.parse::<u8>()?; if n > 1 { return Ok(1); } Ok(n) }",
            ONE_PARAMETER
        );
    final BasicBlock first = graph.blocks().get(graph.entry());

    assertThat(reachesExit(graph, first.id())).isTrue();
    assertThat(first.successors()).hasSizeGreaterThan(1);
  }
}
