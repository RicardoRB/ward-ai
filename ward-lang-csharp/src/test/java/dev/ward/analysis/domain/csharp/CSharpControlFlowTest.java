package dev.ward.analysis.domain.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdge;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

class CSharpControlFlowTest {
  private static final int MIN_LOOP_BLOCKS = 8;

  private static ControlFlowResult lower(final String source) {
    final SourceUnit unit = new SourceUnit("m.cs", AnalysisLanguage.CSHARP, source);
    return new CSharpControlFlow().lower(unit, new AntlrLanguageFrontend().parse(unit).tree());
  }

  private static ControlFlowGraph graph(final String source, final String name) {
    return lower(source).graphs().stream()
        .filter(candidate -> candidate.body().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError(name + " in " + lower(source).graphs()));
  }

  private static boolean hasEdge(final ControlFlowGraph graph, final FlowEdgeKind kind) {
    return graph.blocks().stream()
        .map(BasicBlock::successors)
        .flatMap(List::stream)
        .map(FlowEdge::kind)
        .anyMatch(kind::equals);
  }

  @Test
  void anEmptyMethodHasOnlyAnEdgeFromEntryToExit() {
    final ControlFlowGraph graph = graph("class C { void Run() { } }", "C#Run(0)");

    assertThat(graph.blocks().get(graph.entry()).successors())
        .containsExactly(new FlowEdge(graph.exit(), FlowEdgeKind.NORMAL));
  }

  @Test
  void anIfBranchesOnItsOutcome() {
    final ControlFlowGraph graph =
        graph("class C { void Run(bool a) { if (a) { X(); } else { Y(); } Z(); } }", "C#Run(1)");

    assertThat(hasEdge(graph, FlowEdgeKind.TRUE)).isTrue();
    assertThat(hasEdge(graph, FlowEdgeKind.FALSE)).isTrue();
  }

  @Test
  void loopsHaveABackEdgeAndBreakLeavesThem() {
    final ControlFlowGraph graph =
        graph(
            "class C { void Run(int n) { while (n > 0) { n--; if (n == 3) { break; } } for (var i = 0; i < n; i++) { continue; } foreach (var x in n) { } do { n++; } while (n < 9); } }",
            "C#Run(1)"
        );

    assertThat(graph.blocks().size()).isGreaterThan(MIN_LOOP_BLOCKS);
    assertThat(hasEdge(graph, FlowEdgeKind.TRUE)).isTrue();
  }

  @Test
  void aThrowInsideTryReachesTheCatch() {
    final ControlFlowGraph graph =
        graph(
            "class C { int Run() { try { throw new System.Exception(); } catch (System.Exception e) { return 1; } finally { Done(); } return 2; } }",
            "C#Run(0)"
        );

    assertThat(hasEdge(graph, FlowEdgeKind.EXCEPTION)).isTrue();
  }

  @Test
  void aSwitchWithoutDefaultCanSkipEverySection() {
    final ControlFlowGraph graph =
        graph(
            "class C { void Run(int k) { switch (k) { case 1: A(); break; case 2: B(); break; } } }",
            "C#Run(1)"
        );

    assertThat(hasEdge(graph, FlowEdgeKind.FALSE)).isTrue();
  }

  @Test
  void constructorsAccessorsAndLocalFunctionsAreBodiesOfTheirOwn() {
    final List<String> names =
        lower(
            "class C { C(int a) { } int P { get { return 1; } } void M() { void Local() { } Local(); } }"
        )
            .graphs().stream()
            .map(ControlFlowGraph::body)
            .toList();

    assertThat(names).containsExactlyInAnyOrder("C#.ctor(1)", "C#.get(0)", "C#M(0)", "C#Local(0)");
  }
}
