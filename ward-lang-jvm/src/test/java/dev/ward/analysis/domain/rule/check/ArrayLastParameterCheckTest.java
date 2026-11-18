package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArrayLastParameterCheckTest {
  private static final ArrayLastParameterCheck CHECK = new ArrayLastParameterCheck("message");

  private static List<String> run(final String member) {
    return new ParsedFacts("class A {\n" + member + "\n}\n").run(CHECK);
  }

  @Test
  void anArrayThatIsTheLastParameterIsReported() {
    assertThat(run("  void f(int a, int[] rest) {}")).containsExactly("2:17");
    assertThat(run("  A(String[] names) {}")).hasSize(1);
  }

  @Test
  void varargsAnArrayThatIsNotLastAndByteArraysAreNotReported() {
    assertThat(run("  void f(int a, int... rest) {}")).isEmpty();
    assertThat(run("  void f(int[] first, int last) {}")).isEmpty();
    assertThat(run("  void f(int a, byte[] bytes) {}")).isEmpty();
    assertThat(run("  void f() {}")).isEmpty();
  }

  @Test
  void theMainMethodIsNotReportedButAnotherMethodWithOneStringArrayIs() {
    assertThat(run("  public static void main(final String[] args) {}")).isEmpty();
    assertThat(run("  static void run(final String[] args) {}")).hasSize(1);
    assertThat(run("  static void main(final int a, final String[] args) {}")).hasSize(1);
  }

  @Test
  @DisplayName("The message of use-varargs prints the parameter with its spaces")
  void messagePrintsTheParameterWithItsSpaces() {
    // Given
    final ParsedFacts facts = new ParsedFacts("class A {\n  void f(final String[] names) {}\n}\n");

    // When
    final List<RuleMatch> found = facts.plain(new ArrayLastParameterCheck("array {value}"));

    // Then
    assertThat(found).extracting(RuleMatch::message).containsExactly("array final String[] names");
  }
}
