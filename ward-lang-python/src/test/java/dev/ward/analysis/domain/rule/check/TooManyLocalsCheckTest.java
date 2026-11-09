package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The functions whose parameters and assigned names exceed the limit. */
final class TooManyLocalsCheckTest {

  private static final TooManyLocalsCheck CHECK = new TooManyLocalsCheck(2, "m");

  @Test
  void aFunctionOverTheLimitIsReported() {
    assertThat(CHECK.matches(ParsedPython.tree("def f(a):\n    b = 1\n    c = 2\n"))).hasSize(1);
  }

  @Test
  void aFunctionAtTheLimitIsNotReported() {
    assertThat(CHECK.matches(ParsedPython.tree("def f(a):\n    b = 1\n"))).isEmpty();
  }
}
