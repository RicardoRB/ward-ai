package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.check.RuleCheck;
import org.junit.jupiter.api.Test;

/** The right-curly check, run over real Swift source. */
class SwiftRightCurlyCheckTest {
  private final RuleCheck check = new SwiftRightCurlyCheck(LintRuns.MESSAGE);

  @Test
  void elseAndCatchOnTheLineOfTheClosingBraceAreNotReported() {
    assertThat(LintRuns.run(check, "if a {\n f()\n} else {\n g()\n}\n")).isEmpty();
    assertThat(LintRuns.run(check, "do {\n f()\n} catch {\n g()\n}\n")).isEmpty();
  }

  @Test
  void elseAndCatchThatStartALineAfterTheClosingBraceAreReported() {
    assertThat(LintRuns.lines(LintRuns.run(check, "if a {\n f()\n}\nelse {\n g()\n}\n")))
        .containsExactly(LintRuns.FOURTH_LINE);
    assertThat(LintRuns.lines(LintRuns.run(check, "do {\n f()\n}\ncatch {\n g()\n}\n")))
        .containsExactly(LintRuns.FOURTH_LINE);
  }

  @Test
  void theElseOfAMultiLineGuardHasNoBraceBeforeIt() {
    assertThat(LintRuns.run(check, "guard let a = x,\n      let b = y\nelse { return }\n"))
        .isEmpty();
  }
}
