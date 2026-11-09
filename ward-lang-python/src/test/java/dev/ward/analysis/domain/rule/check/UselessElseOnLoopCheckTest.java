package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The loop else clauses that no break of their own can reach. */
final class UselessElseOnLoopCheckTest {

  private static final UselessElseOnLoopCheck CHECK = new UselessElseOnLoopCheck("m");

  @Test
  void forAndWhileElseWithoutABreakAreReported() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree("for i in items:\n    print(i)\nelse:\n    print('d')\n")))
        .hasSize(1);
    assertThat(CHECK.matches(ParsedPython.tree("while x:\n    x = 0\nelse:\n    print('d')\n")))
        .hasSize(1);
  }

  @Test
  void elseWithABreakOfItsOwnIsNotReported() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "for i in items:\n    if i:\n        break\nelse:\n    print('d')\n")))
        .isEmpty();
  }

  @Test
  void aBreakInsideANestedLoopDoesNotCountForTheOuterLoop() {
    assertThat(
            CHECK.matches(
                ParsedPython.tree(
                    "for i in items:\n    for j in i:\n        break\nelse:\n    print('d')\n")))
        .hasSize(1);
  }

  @Test
  void aLoopWithoutElseIsNotReported() {
    assertThat(CHECK.matches(ParsedPython.tree("for i in items:\n    print(i)\n"))).isEmpty();
  }
}
