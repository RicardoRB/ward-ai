package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The global names that a function reads without assigning them. */
final class GlobalVariableNotAssignedCheckTest {

  private static final GlobalVariableNotAssignedCheck CHECK =
      new GlobalVariableNotAssignedCheck("m");

  @Test
  void aGlobalThatIsOnlyReadIsReported() {
    assertThat(CHECK.matches(ParsedPython.tree("def f():\n    global c\n    return c\n")))
        .hasSize(1);
  }

  @Test
  void aGlobalThatIsAssignedIsNotReported() {
    assertThat(CHECK.matches(ParsedPython.tree("def f():\n    global c\n    c = 1\n"))).isEmpty();
    assertThat(
            CHECK.matches(
                ParsedPython.tree("def f():\n    global c\n    for c in x:\n        pass\n")))
        .isEmpty();
  }

  @Test
  void oneUnassignedNameAmongSeveralIsReported() {
    assertThat(CHECK.matches(ParsedPython.tree("def f():\n    global c, d\n    d += 1\n")))
        .hasSize(1);
  }
}
