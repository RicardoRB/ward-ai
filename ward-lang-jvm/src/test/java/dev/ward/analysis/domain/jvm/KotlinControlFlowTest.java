package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The graphs that the Kotlin lowering builds: which bodies, and how statements join. */
class KotlinControlFlowTest {

  private static final String AFTER = "z()";

  private static ControlFlowGraph of(final String body) {
    return KotlinFlowFixtures.graph(
        "class C {\n  fun f(c: Boolean) {\n" + body + "\n  }\n}\n", "C#f(1)");
  }

  private static int block(final String body, final String snippet) {
    return JavaFlowFixtures.blockWith(
        of(body), "class C {\n  fun f(c: Boolean) {\n" + body + "\n  }\n}\n", snippet);
  }

  @Test
  void everyFunctionConstructorAndInitializerIsABodyAndNestedOnesToo() {
    final String source =
        "package p\n"
            + "class C(val a: Int) {\n"
            + "  init { println(a) }\n"
            + "  constructor(a: Int, b: Int) : this(a) { println(b) }\n"
            + "  fun f(x: Int) { fun local(y: Int) { println(y) }\n local(x) }\n"
            + "  fun g() = 1\n"
            + "  fun noBody(): Int\n"
            + "  companion object {\n fun make() { }\n }\n"
            + "}\n"
            + "fun top() { }\n";

    assertThat(KotlinFlowFixtures.lower(source).graphs())
        .extracting(ControlFlowGraph::body)
        .containsExactly(
            "C#<init>(0)",
            "C#<init>(2)",
            "C#f(1)",
            "C#local(1)",
            "C#g(0)",
            "C.Companion#make(0)",
            "#top(0)");
  }

  @Test
  void anIfJoinsItsBranchesAndSplitsOnTheCondition() {
    final String body = "var x = 1\nif (c) { x = 2 } else { x = 3 }\nprintln(x)";
    final ControlFlowGraph graph = of(body);
    final int condition = block(body, "c");
    final int then = block(body, "x = 2");
    final int otherwise = block(body, "x = 3");
    final int join = block(body, "println(x)");

    assertThat(JavaFlowFixtures.hasEdge(graph, condition, then, FlowEdgeKind.TRUE)).isTrue();
    assertThat(JavaFlowFixtures.hasEdge(graph, condition, otherwise, FlowEdgeKind.FALSE)).isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, then, join)).isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, otherwise, join)).isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, then, otherwise)).isFalse();
  }

  @Test
  void anIfWithoutElseFallsThroughAndAnElseIfChainsOn() {
    final String body = "if (c) { a() } else if (c) { b() }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(JavaFlowFixtures.reachable(graph, block(body, "c"), block(body, AFTER))).isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "a()"), block(body, "b()"))).isFalse();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "b()"), block(body, AFTER))).isTrue();
  }

  @Test
  void aWhenTestsEntriesInTurnAndElseCatchesTheRest() {
    final String body = "when (c) { true -> a()\n false -> b()\n else -> d() }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(JavaFlowFixtures.reachable(graph, block(body, "true"), block(body, "false")))
        .isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "a()"), block(body, "b()"))).isFalse();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "false"), block(body, "d()")))
        .isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "d()"), block(body, AFTER))).isTrue();
  }

  @Test
  void aWhenWithoutElseCanSkipEveryEntry() {
    final String body = "when { c -> a() }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(JavaFlowFixtures.reachable(graph, block(body, "c"), block(body, AFTER))).isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "a()"), block(body, AFTER))).isTrue();
  }

  @Test
  void anExpressionBodyReturnsItsValue() {
    final ControlFlowGraph graph =
        KotlinFlowFixtures.graph("class C {\n fun f(x: String) = x.trim()\n}\n", "C#f(1)");

    assertThat(graph.blocks().get(0).nodes())
        .extracting(SyntaxNode::kind)
        .containsExactly("returnStatement");
    assertThat(graph.blocks().get(0).nodes().get(0).children()).hasSize(2);
  }

  @Test
  void aForLoopVariableAndItsIterableAreOneStep() {
    final String body = "for (p in items(c)) { use(p) }";
    final ControlFlowGraph graph = of(body);

    assertThat(KotlinFlowFixtures.accesses(graph)).contains("p:c", "r:c", "w:p", "r:p");
  }
}
