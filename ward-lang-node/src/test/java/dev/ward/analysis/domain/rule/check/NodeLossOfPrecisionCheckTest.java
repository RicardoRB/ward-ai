package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reports a number literal whose value cannot hold the digits written, as ESLint does. */
class NodeLossOfPrecisionCheckTest {

  private static final String MESSAGE = "This number literal will lose precision at runtime.";
  private static final int COLUMN = 11;

  /** Scenario: an integer beyond the exact range is reported at its first digit. */
  @Test
  void anIntegerBeyondTheExactRangeIsReported() {
    final List<RuleMatch> found = run("const a = 9007199254740993;\n");

    assertThat(found).hasSize(1);
    assertThat(found.get(0).span().startColumn()).isEqualTo(COLUMN);
    assertThat(found.get(0).message()).isEqualTo(MESSAGE);
  }

  /** Scenario: a value that is exact, and a decimal that round-trips, are not reported. */
  @Test
  void exactValuesAreNotReported() {
    assertThat(run("const a = 9007199254740992;\nconst b = 0.30000000000000004;\n")).isEmpty();
  }

  /**
   * Scenario: a prefixed octal, a binary literal and a legacy octal that are exact are not
   * reported.
   */
  @Test
  void exactNonDecimalLiteralsAreNotReported() {
    assertThat(run("const a = 0o777;\nconst b = 0b1111;\nconst c = 0777;\n")).isEmpty();
  }

  /**
   * Scenario: a hexadecimal and a binary literal with more digits than a double keeps are reported.
   */
  @Test
  void lossyHexAndBinaryLiteralsAreReported() {
    assertThat(run("const a = 0x1ffffffffffffffffff;\n")).hasSize(1);
    assertThat(run("const a = 0b" + "1".repeat(65) + ";\n")).hasSize(1);
  }

  /** Scenario: an octal literal whose value keeps every digit written is not reported. */
  @Test
  void aPrefixedOctalLiteralKeepingItsDigitsIsNotReported() {
    assertThat(run("const a = 0o20000000000000001;\n")).isEmpty();
  }

  /** Scenario: a literal needing more than 100 significant digits is reported. */
  @Test
  void moreThan100SignificantDigitsAreReported() {
    assertThat(run("const a = " + "1".repeat(101) + ";\n")).hasSize(1);
  }

  /**
   * Scenario: a value that overflows to infinity is reported; one that underflows to zero is not.
   */
  @Test
  void overflowIsReportedAndUnderflowIsNot() {
    assertThat(run("const a = 1e400;\n")).hasSize(1);
    assertThat(run("const a = 123e-400;\n")).isEmpty();
  }

  private static List<RuleMatch> run(final String source) {
    final SourceUnit unit = new SourceUnit("a", AnalysisLanguage.JAVASCRIPT, source);
    final SyntaxNode root = new AntlrLanguageFrontend().parse(unit).tree();
    return new NodeLossOfPrecisionCheck(MESSAGE).matches(root);
  }
}
