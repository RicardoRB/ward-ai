package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.KotlinFlowFixtures.accesses;
import static dev.ward.analysis.domain.jvm.KotlinFlowFixtures.graph;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The variable accesses of Kotlin bodies, which come from the scope analysis of the file. */
class KotlinAccessesTest {

  private static final String PARAM_A = "p:a";
  private static final String READ_A = "r:a";

  private static List<String> of(final String function, final int parameters) {
    return accesses(graph("class C {\n" + function + "\n}\n", "C#f(" + parameters + ")"));
  }

  @Test
  void parametersAreBoundAtTheEntry() {
    assertThat(of("fun f(a: Int, b: String) {}", 2)).containsExactly(PARAM_A, "p:b");
  }

  @Test
  void aDeclarationWithAValueReadsTheInitializerThenWritesTheName() {
    assertThat(of("fun f(a: Int) { val b = a + 1 }", 1)).containsExactly(PARAM_A, READ_A, "w:b");
  }

  @Test
  void aCompoundAssignmentReadsBeforeItWrites() {
    assertThat(of("fun f(a: Int) { var b = 0\n b += a\n b++ }", 1))
        .containsExactly(PARAM_A, "w:b", "r:b", READ_A, "w:b", "r:b", "w:b");
  }

  @Test
  void anAssignmentReadsItsValueBeforeItsTarget() {
    assertThat(of("fun f(a: String) { var s = a\n s = s + a }", 1))
        .containsExactly(PARAM_A, READ_A, "w:s", "r:s", READ_A, "w:s");
  }

  @Test
  void namesInStringTemplatesAreReads() {
    assertThat(of("fun f(a: String, n: Int) { val s = \"x $a ${n + 1}\" }", 2))
        .containsExactly(PARAM_A, "p:n", READ_A, "r:n", "w:s");
  }

  @Test
  void propertiesAndNamesOfOtherBodiesAreNotRecorded() {
    final String source =
        "class C {\n  val field = 1\n  fun f(a: Int) { fun inner(b: Int) { println(b + a) }\n println(field + a) }\n}\n";

    assertThat(accesses(graph(source, "C#f(1)"))).containsExactly(PARAM_A, READ_A);
    assertThat(accesses(graph(source, "C#inner(1)"))).containsExactly("p:b", "r:b");
  }
}
