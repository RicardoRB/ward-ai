package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import org.junit.jupiter.api.Test;

/** The loops, handlers and jumps of Kotlin bodies in the graphs the lowering builds. */
class KotlinJumpFlowTest {

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
  void loopsHaveBackEdgesAndBreaksLeaveThem() {
    final String body = "while (c) { if (c) { break }\nstep() }\nz()";
    final ControlFlowGraph graph = of(body);
    final int step = block(body, "step()");

    assertThat(JavaFlowFixtures.reachable(graph, step, block(body, "c"))).isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "break"), step)).isFalse();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "break"), block(body, AFTER)))
        .isTrue();
  }

  @Test
  void aDoWhileRunsItsBodyBeforeTheTest() {
    final String body = "do { step() } while (c)\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(JavaFlowFixtures.reachable(graph, block(body, "step()"), block(body, AFTER)))
        .isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, AFTER), block(body, "step()")))
        .isFalse();
  }

  @Test
  void aLabelledBreakLeavesTheOuterLoop() {
    final String body =
        "outer@ for (i in 1..3) { for (j in 1..3) { if (c) { break@outer }\n"
            + "inner() }\nafterInner() }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(JavaFlowFixtures.reachable(graph, block(body, "break@outer"), block(body, AFTER)))
        .isTrue();
    assertThat(
            JavaFlowFixtures.reachable(
                graph, block(body, "break@outer"), block(body, "afterInner()")))
        .isFalse();
  }

  @Test
  void aContinueGoesBackToItsLoopAndSkipsTheRestOfTheBody() {
    final String body = "while (c) { if (c) { continue }\nrest() }";
    final ControlFlowGraph graph = of(body);

    assertThat(
            JavaFlowFixtures.hasEdge(
                graph, block(body, "continue"), block(body, "c"), FlowEdgeKind.NORMAL))
        .isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "continue"), block(body, "rest()")))
        .isTrue();
  }

  @Test
  void aCatchIsReachableFromACallInTheTryAndFinallyFollowsBoth() {
    final String body = "try { risky() } catch (e: Exception) { log(e) } finally { done() }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(JavaFlowFixtures.reachable(graph, block(body, "risky()"), block(body, "log(e)")))
        .isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "log(e)"), block(body, "done()")))
        .isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "risky()"), block(body, "done()")))
        .isTrue();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "done()"), block(body, AFTER)))
        .isTrue();
  }

  @Test
  void aReturnAndAThrowEndTheirPath() {
    final String body = "if (c) { return }\nif (c) { throw IllegalStateException() }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(JavaFlowFixtures.reachable(graph, block(body, "return"), block(body, AFTER)))
        .isFalse();
    assertThat(JavaFlowFixtures.reachable(graph, block(body, "throw"), block(body, AFTER)))
        .isFalse();
  }

  @Test
  void aLabelledReturnLeavesTheLambdaNotTheBody() {
    final String body = "listOf(1).forEach { if (c) return@forEach }\nz()";
    final ControlFlowGraph graph = of(body);

    assertThat(JavaFlowFixtures.reachable(graph, block(body, "return@forEach"), block(body, AFTER)))
        .isTrue();
  }
}
