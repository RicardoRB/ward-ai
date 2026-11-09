package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The trailing bare returns, and the bodies that end with something else. */
final class UselessReturnCheckTest {

  private static final UselessReturnCheck CHECK = new UselessReturnCheck("m");

  @Test
  void aBareReturnOrReturnNoneAsTheLastStatementIsReported() {
    assertThat(CHECK.matches(ParsedPython.tree("def f(x):\n    print(x)\n    return\n")))
        .hasSize(1);
    assertThat(CHECK.matches(ParsedPython.tree("def f(x):\n    return None\n"))).hasSize(1);
  }

  @Test
  void aOneLineBodyIsChecked() {
    assertThat(CHECK.matches(ParsedPython.tree("def f(): return\n"))).hasSize(1);
  }

  @Test
  void aReturnWithAValueIsNotReported() {
    assertThat(CHECK.matches(ParsedPython.tree("def f(x):\n    return x\n"))).isEmpty();
  }

  @Test
  void aReturnInsideAnEndingCompoundStatementIsNotReported() {
    assertThat(CHECK.matches(ParsedPython.tree("def f(x):\n    if x:\n        return\n")))
        .isEmpty();
  }
}
