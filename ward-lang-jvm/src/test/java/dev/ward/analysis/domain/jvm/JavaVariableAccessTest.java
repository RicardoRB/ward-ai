package dev.ward.analysis.domain.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the variable accesses that the Java lowering records on each block. */
class JavaVariableAccessTest {

  @Test
  void selfAssignmentReadsBeforeItWrites() {
    final String source = "class A { void f() { int x = 0; x = x + 1; } }";

    assertThat(JavaAccessFixtures.kinds(JavaAccessFixtures.accessesOf(source, "A#f(0)"), 0))
        .containsExactly("DECLARE x", "WRITE x", "READ x", "WRITE x");
  }

  @Test
  void compoundAssignmentAndIncrementReadThenWrite() {
    final String source = "class A { void f(int p) { int x = 0; x += p; x++; } }";

    assertThat(JavaAccessFixtures.kinds(JavaAccessFixtures.accessesOf(source, "A#f(1)"), 0))
        .containsExactly(
            "PARAMETER p",
            "DECLARE x",
            "WRITE x",
            "READ x",
            "READ p",
            "WRITE x",
            "READ x",
            "WRITE x");
  }

  @Test
  void lambdaCreationCapturesTheOuterVariableAtItsPosition() {
    final String source =
        "class A { void f(int total) { int seen = 1; Runnable r = () -> use(total); } }";
    final List<VariableAccess> accesses =
        JavaAccessFixtures.accessesOf(source, "A#f(1)").get(0).accesses();

    final VariableAccess capture =
        accesses.stream().filter(access -> access.kind() == AccessKind.CAPTURE).findFirst().get();
    assertThat(capture.name()).isEqualTo("total");
    assertThat(source.substring(capture.span().startOffset(), capture.span().endOffset()))
        .startsWith("() -> use(total)");
  }

  @Test
  void lambdaBodyDoesNotRecordOuterNames() {
    final String source = "class A { void f(int total) { Runnable r = () -> use(total); } }";

    final ControlFlowGraph lambda = JavaAccessFixtures.graphNamed(source, "<lambda>@1:");

    assertThat(lambda.blocks().get(0).accesses()).isEmpty();
  }

  @Test
  void fieldWritesAndUnresolvedNamesAreNotRecorded() {
    final String source = "class A { int count; void f() { this.count = 1; count = 2; } }";

    assertThat(JavaAccessFixtures.accessesOf(source, "A#f(0)").get(0).accesses()).isEmpty();
  }

  @Test
  void parametersAreIncomingWritesInTheEntryBlock() {
    final String source = "class A { int f(int a, int b) { return a; } }";

    assertThat(JavaAccessFixtures.kinds(JavaAccessFixtures.accessesOf(source, "A#f(2)"), 0))
        .containsExactly("PARAMETER a", "PARAMETER b", "READ a");
  }

  @Test
  void sameNameInSiblingBlocksIsTwoVariables() {
    final String source =
        "class A { void f(boolean c) { if (c) { int v = 1; use(v); } else { int v = 2; use(v); } } }";
    final ControlFlowGraph graph = JavaAccessFixtures.graphNamed(source, "A#f(1)");
    final List<VariableAccess> declarations =
        graph.blocks().stream()
            .flatMap(block -> block.accesses().stream())
            .filter(access -> access.kind() == AccessKind.DECLARE)
            .toList();

    assertThat(declarations).hasSize(2);
    assertThat(declarations.get(0).declaration()).isNotEqualTo(declarations.get(1).declaration());
  }

  @Test
  void enhancedForVariableIsDeclaredAndWrittenEachIteration() {
    final String source =
        "class A { void f(List<String> items) { for (String s : items) { use(s); } } }";

    final List<String> kinds =
        JavaAccessFixtures.kinds(JavaAccessFixtures.accessesOf(source, "A#f(1)"));

    assertThat(kinds)
        .containsExactly("PARAMETER items", "READ items", "DECLARE s", "WRITE s", "READ s");
  }

  @Test
  void lambdaParameterIsAnIncomingWriteOfItsOwnBody() {
    final String source =
        "class A { void f() { java.util.function.IntUnaryOperator g = z -> z + 1; } }";

    final ControlFlowGraph lambda = JavaAccessFixtures.graphNamed(source, "<lambda>@1:");

    assertThat(JavaAccessFixtures.kinds(List.of(lambda.blocks().get(0))))
        .containsExactly("PARAMETER z", "READ z");
  }
}
