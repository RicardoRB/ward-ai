package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The loop and except targets that rebind an argument, and the ones that do not. */
final class RedefinedArgumentFromLocalCheckTest {

  private static final RedefinedArgumentFromLocalCheck CHECK =
      new RedefinedArgumentFromLocalCheck("m");

  @Test
  void aLoopTargetNamedLikeAnArgumentIsReported() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree("def f(value):\n    for value in items:\n        pass\n")))
        .hasSize(1);
  }

  @Test
  void anExceptNameLikeAnArgumentIsReported() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "def f(value):\n    try:\n        pass\n    except E as value:\n        pass\n")))
        .hasSize(1);
  }

  @Test
  void otherTargetsAndUnnamedExceptsAreNotReported() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree("def f(value):\n    for item in value:\n        pass\n")))
        .isEmpty();
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "def f(value):\n    try:\n        pass\n    except E:\n        pass\n")))
        .isEmpty();
  }
}
