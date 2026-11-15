package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.KotlinFlowFixtures.accesses;
import static dev.ward.analysis.domain.jvm.KotlinFlowFixtures.graph;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The variable accesses of Kotlin bodies, which come from the scope analysis of the file. */
class KotlinAccessesScopeTest {

  private static final String PARAM_A = "p:a";
  private static final String READ_A = "r:a";

  private static List<String> of(final String function, final int parameters) {
    return accesses(graph("class C {\n" + function + "\n}\n", "C#f(" + parameters + ")")).stream()
        .filter(access -> !access.contains("$"))
        .toList();
  }

  @Test
  void aLambdaIsLoweredInPlaceSoScopeFunctionsReadAndWriteTheNamesAroundThem() {
    assertThat(of("fun f(a: String) { var t = \"\"\n val u = a.let { t = it\n it + t } }", 1))
        .contains(READ_A, "w:t", "w:u");
  }

  @Test
  void theStatementsOfALambdaKeepTheirOrder() {
    assertThat(of("fun f(a: String) { run { val t = a\n use(t) } }", 1))
        .containsExactly(PARAM_A, READ_A, "w:t", "r:t");
  }

  @Test
  void aCatchParameterIsWrittenOnEntryToTheHandler() {
    assertThat(of("fun f() { try { risky() } catch (e: Exception) { log(e) } }", 0))
        .containsExactly("w:e", "r:e");
  }

  @Test
  void aForVariableIsWrittenWithTheNamesOfItsIterableRead() {
    assertThat(of("fun f(items: List<String>) { for (p in items) { println(p) } }", 1))
        .containsExactly("p:items", "r:items", "w:p", "r:p");
  }

  @Test
  void aDestructuringDeclarationWritesEveryName() {
    assertThat(of("fun f(a: Pair<Int, Int>) { val (x, y) = a }", 1))
        .containsExactly(PARAM_A, READ_A, "w:x", "w:y");
  }

  @Test
  void aPrefixIncrementReadsThenWritesTheName() {
    assertThat(of("fun f(a: Int) { var b = a\n ++b }", 1))
        .containsExactly(PARAM_A, READ_A, "w:b", "r:b", "w:b");
  }
}
