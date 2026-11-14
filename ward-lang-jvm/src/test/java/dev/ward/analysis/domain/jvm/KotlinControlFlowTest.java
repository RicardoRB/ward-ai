package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.JavaFlowFixtures.blockWith;
import static dev.ward.analysis.domain.jvm.JavaFlowFixtures.hasEdge;
import static dev.ward.analysis.domain.jvm.JavaFlowFixtures.reachable;
import static dev.ward.analysis.domain.jvm.KotlinFlowFixtures.graph;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import org.junit.jupiter.api.Test;

/** The graphs that the Kotlin lowering builds: which bodies, and how statements join. */
class KotlinControlFlowTest {

  private static ControlFlowGraph of(final String body) {
    return graph("class C {\n  fun f(c: Boolean) {\n" + body + "\n  }\n}\n", "C#f(1)");
  }

  private static int block(final String body, final String snippet) {
    return blockWith(of(body), "class C {\n  fun f(c: Boolean) {\n" + body + "\n  }\n}\n", snippet);
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

    assertThat(hasEdge(graph, condition, then, FlowEdgeKind.TRUE)).isTrue();
    assertThat(hasEdge(graph, condition, otherwise, FlowEdgeKind.FALSE)).isTrue();
    assertThat(reachable(graph, then, join)).isTrue();
    assertThat(reachable(graph, otherwise, join)).isTrue();
    assertThat(reachable(graph, then, otherwise)).isFalse();
  }

  @Test
  void anIfWithoutElseFallsThroughAndAnElseIfChainsOn() {
    final String body = "if (c) { a() } else if (c) { b() }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(reachable(graph, block(body, "c"), block(body, "z()"))).isTrue();
    assertThat(reachable(graph, block(body, "a()"), block(body, "b()"))).isFalse();
    assertThat(reachable(graph, block(body, "b()"), block(body, "z()"))).isTrue();
  }

  @Test
  void aWhenTestsEntriesInTurnAndElseCatchesTheRest() {
    final String body = "when (c) { true -> a()\n false -> b()\n else -> d() }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(reachable(graph, block(body, "true"), block(body, "false"))).isTrue();
    assertThat(reachable(graph, block(body, "a()"), block(body, "b()"))).isFalse();
    assertThat(reachable(graph, block(body, "false"), block(body, "d()"))).isTrue();
    assertThat(reachable(graph, block(body, "d()"), block(body, "z()"))).isTrue();
  }

  @Test
  void aWhenWithoutElseCanSkipEveryEntry() {
    final String body = "when { c -> a() }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(reachable(graph, block(body, "c"), block(body, "z()"))).isTrue();
    assertThat(reachable(graph, block(body, "a()"), block(body, "z()"))).isTrue();
  }

  @Test
  void loopsHaveBackEdgesAndBreaksLeaveThem() {
    final String body = "while (c) { if (c) { break }\nstep() }\nz()";
    final ControlFlowGraph graph = of(body);
    final int step = block(body, "step()");

    assertThat(reachable(graph, step, block(body, "c"))).isTrue();
    assertThat(reachable(graph, block(body, "break"), step)).isFalse();
    assertThat(reachable(graph, block(body, "break"), block(body, "z()"))).isTrue();
  }

  @Test
  void aDoWhileRunsItsBodyBeforeTheTest() {
    final String body = "do { step() } while (c)\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(reachable(graph, block(body, "step()"), block(body, "z()"))).isTrue();
    assertThat(reachable(graph, block(body, "z()"), block(body, "step()"))).isFalse();
  }

  @Test
  void aLabelledBreakLeavesTheOuterLoop() {
    final String body =
        "outer@ for (i in 1..3) { for (j in 1..3) { if (c) { break@outer }\n"
            + "inner() }\nafterInner() }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(reachable(graph, block(body, "break@outer"), block(body, "z()"))).isTrue();
    assertThat(reachable(graph, block(body, "break@outer"), block(body, "afterInner()"))).isFalse();
  }

  @Test
  void aContinueGoesBackToItsLoopAndSkipsTheRestOfTheBody() {
    final String body = "while (c) { if (c) { continue }\nrest() }";
    final ControlFlowGraph graph = of(body);

    assertThat(hasEdge(graph, block(body, "continue"), block(body, "c"), FlowEdgeKind.NORMAL))
        .isTrue();
    assertThat(reachable(graph, block(body, "continue"), block(body, "rest()"))).isTrue();
  }

  @Test
  void aCatchIsReachableFromACallInTheTryAndFinallyFollowsBoth() {
    final String body = "try { risky() } catch (e: Exception) { log(e) } finally { done() }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(reachable(graph, block(body, "risky()"), block(body, "log(e)"))).isTrue();
    assertThat(reachable(graph, block(body, "log(e)"), block(body, "done()"))).isTrue();
    assertThat(reachable(graph, block(body, "risky()"), block(body, "done()"))).isTrue();
    assertThat(reachable(graph, block(body, "done()"), block(body, "z()"))).isTrue();
  }

  @Test
  void aReturnAndAThrowEndTheirPath() {
    final String body = "if (c) { return }\nif (c) { throw IllegalStateException() }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(reachable(graph, block(body, "return"), block(body, "z()"))).isFalse();
    assertThat(reachable(graph, block(body, "throw"), block(body, "z()"))).isFalse();
  }

  @Test
  void aLabelledReturnLeavesTheLambdaNotTheBody() {
    final String body = "listOf(1).forEach { if (c) return@forEach }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(reachable(graph, block(body, "return@forEach"), block(body, "z()"))).isTrue();
  }

  @Test
  void anExpressionBodyReturnsItsValue() {
    final ControlFlowGraph graph = graph("class C {\n fun f(x: String) = x.trim()\n}\n", "C#f(1)");

    assertThat(graph.blocks().get(0).nodes())
        .extracting(node -> node.kind())
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
