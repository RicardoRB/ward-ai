package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** StopIteration raised inside a generator, and the raises that do not match. */
final class StopIterationReturnCheckTest {

  private static final StopIterationReturnCheck CHECK = new StopIterationReturnCheck("m");

  @Test
  void raisingStopIterationInsideAGeneratorIsReported() {
    assertThat(CHECK.matches(ParsedPython.tree("def g():\n    yield 1\n    raise StopIteration\n")))
        .hasSize(1);
  }

  @Test
  void raisingStopIterationOutsideAGeneratorIsNotReported() {
    assertThat(CHECK.matches(ParsedPython.tree("def f():\n    raise StopIteration\n"))).isEmpty();
  }

  @Test
  void otherExceptionsAndLongerNamesAreNotReported() {
    assertThat(CHECK.matches(ParsedPython.tree("def g():\n    yield 1\n    raise ValueError\n")))
        .isEmpty();
    assertThat(
            CHECK.matches(
                ParsedPython.tree("def g():\n    yield 1\n    raise StopIterationError()\n")))
        .isEmpty();
  }
}
