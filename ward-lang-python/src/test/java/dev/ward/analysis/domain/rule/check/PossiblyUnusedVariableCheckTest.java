package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.port.AnalysisFailedException;
import org.junit.jupiter.api.Test;

/** The possibly-unused-variable check: a local that a later {@code locals()} may read. */
final class PossiblyUnusedVariableCheckTest {

  private static final String MESSAGE = "possibly unused";

  @Test
  void reportsAnUnreadLocalWhenLocalsIsCalledLater() {
    assertThat(
            PythonSources.positions(
                PythonSources.scoped(
                    new PossiblyUnusedVariableCheck(MESSAGE),
                    "def f():\n    x = 1\n    return locals()\n")))
        .containsExactly("2:5");
  }

  @Test
  void acceptsALocalThatIsReadAndALocalsCallBeforeTheBinding() {
    assertThat(
            PythonSources.scoped(
                new PossiblyUnusedVariableCheck(MESSAGE),
                "def f():\n    x = 1\n    print(x)\n    return locals()\n"))
        .isEmpty();
    assertThat(
            PythonSources.scoped(
                new PossiblyUnusedVariableCheck(MESSAGE),
                "def g():\n    return locals()\n    y = 2\n"))
        .isEmpty();
  }

  @Test
  void ignoresLocalsCallsInsideNestedFunctionsAndLambdas() {
    assertThat(
            PythonSources.scoped(
                new PossiblyUnusedVariableCheck(MESSAGE),
                "def h():\n    z = 1\n    def inner():\n        return locals()\n    return inner\n"))
        .isEmpty();
    assertThat(
            PythonSources.scoped(
                new PossiblyUnusedVariableCheck(MESSAGE),
                "def m():\n    v = 1\n    return lambda: locals()\n"))
        .isEmpty();
  }

  @Test
  void acceptsAFunctionWithoutLocalsCall() {
    assertThat(
            PythonSources.scoped(new PossiblyUnusedVariableCheck(MESSAGE), "def k():\n    w = 1\n"))
        .isEmpty();
  }

  @Test
  void refusesToRunWithoutScopes() {
    assertThatThrownBy(
            () -> new PossiblyUnusedVariableCheck(MESSAGE).matches(PythonSources.tree("")))
        .isInstanceOf(AnalysisFailedException.class);
  }
}
