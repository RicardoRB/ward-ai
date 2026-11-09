package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The functions that return a value on some path and nothing on another, like Pylint R1710. */
final class InconsistentReturnCheckTest {

  private static final InconsistentReturnCheck CHECK = new InconsistentReturnCheck("m");

  @Test
  void aFallThroughEndNextToAValueReturnIsReported() {
    assertThat(CHECK.matches(ParsedPython.tree("def f(c):\n    if c:\n        return 1\n")))
        .hasSize(1);
  }

  @Test
  void aBareReturnNextToAValueReturnIsReported() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "def f(c):\n    if c:\n        return 1\n    else:\n        return\n")))
        .hasSize(1);
  }

  @Test
  void aBodyThatEndsInAValueReturnIsConsistent() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree("def f(c):\n    if c:\n        return 1\n    return 2\n")))
        .isEmpty();
  }

  @Test
  void returnNoneCountsAsAValue() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree("def f(c):\n    if c:\n        return None\n    return None\n")))
        .isEmpty();
  }

  @Test
  void aFunctionWithOnlyBareReturnsIsConsistent() {
    assertThat(CHECK.matches(ParsedPython.tree("def f():\n    return\n"))).isEmpty();
  }

  @Test
  void anEndlessLoopEndsTheBody() {
    assertThat(CHECK.matches(ParsedPython.tree("def f():\n    while True:\n        return 1\n")))
        .isEmpty();
  }

  @Test
  void aNestedFunctionReturnsForItself() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree("def f():\n    def g():\n        return 1\n    return g\n")))
        .isEmpty();
  }
}
