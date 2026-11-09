package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.PythonSources.positions;
import static dev.ward.analysis.domain.rule.check.PythonSources.tree;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The overload check reports an overload that is not right after the earlier ones of its name. */
final class PythonOverloadOrderCheckTest {

  private static java.util.List<String> run(final String source) {
    return positions(new PythonOverloadOrderCheck("overloads apart").matches(tree(source)));
  }

  @Test
  void adjacentOverloadsAreNotReported() {
    final String source =
        "from typing import overload\n\n@overload\ndef f(a: int) -> int: ...\n\n"
            + "@overload\ndef f(a: str) -> str: ...\n";
    assertThat(run(source)).isEmpty();
  }

  @Test
  void overloadAfterAnotherDefinitionIsReported() {
    final String source =
        "@overload\ndef f(a: int) -> int: ...\n\ndef g():\n    pass\n\n"
            + "@overload\ndef f(a: str) -> str: ...\n";
    assertThat(run(source)).containsExactly("8:1");
  }

  @Test
  void attributeDecoratorIsAnOverload() {
    final String source =
        "@typing.overload\ndef f(a: int): ...\ndef g(): pass\n"
            + "@typing.overload\ndef f(a: str): ...\n";
    assertThat(run(source)).containsExactly("5:1");
  }

  @Test
  void otherDecoratorsBreakTheGroup() {
    final String source =
        "@overload\ndef f(a: int): ...\n@property\ndef g(self):\n    return 1\n"
            + "@overload\ndef f(a: str): ...\n";
    assertThat(run(source)).containsExactly("7:1");
  }

  @Test
  void singleOverloadIsNotReported() {
    assertThat(run("@overload\ndef f(a: int): ...\n")).isEmpty();
  }

  @Test
  void overloadsOfAClassBodyAreChecked() {
    final String source =
        "class A:\n    @overload\n    def f(self, a: int): ...\n    def g(self):\n"
            + "        pass\n    @overload\n    def f(self, a: str): ...\n";
    assertThat(run(source)).containsExactly("7:5");
  }
}
