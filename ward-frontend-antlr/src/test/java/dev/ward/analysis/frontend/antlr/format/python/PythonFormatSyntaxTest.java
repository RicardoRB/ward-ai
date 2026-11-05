package dev.ward.analysis.frontend.antlr.format.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.FormatRule;
import dev.ward.analysis.domain.format.FormatSafety;
import dev.ward.analysis.domain.format.FormatSyntaxTree;
import dev.ward.analysis.domain.format.FormatToken;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Checks what the Python formatter's syntax view and refusals report. */
class PythonFormatSyntaxTest {

  private static final FormatOptions OPTIONS =
      new FormatOptions(
          Map.of("indent-size", "4", "max-line-length", "100", "max-blank-lines", "1"));

  @Test
  void reportsTheLineOfTheFirstSyntaxError() {
    final FormatSyntaxTree tree = new PythonFormatSyntax().parse("x = 1\ndef broken(:\n    pass\n");

    assertThat(tree.errorLine()).isEqualTo(OptionalInt.of(2));
  }

  @Test
  void commentsAreTokensAndTheFinalLineBreakIsLeftOut() {
    final FormatSyntaxTree tree = new PythonFormatSyntax().parse("x = 1  # note\n");

    assertThat(tree.tokens())
        .extracting(FormatToken::type)
        .containsExactly("NAME", "'='", "NUMBER", "comment");
  }

  @Test
  void aSourceWithoutFinalLineBreakHasTheSameTokens() {
    final PythonFormatSyntax syntax = new PythonFormatSyntax();

    assertThat(syntax.parse("x = 1").tokens()).isEqualTo(syntax.parse("x = 1\n").tokens());
  }

  @Test
  void refusesAnUnknownCharacter() {
    final FormatSafety safety = new FormatSafety(new PythonFormatSyntax());

    final FormatResult result = safety.formatSafely(new PythonFormatter(), OPTIONS, "x = $\n");

    assertThat(result).isInstanceOf(FormatResult.Refused.class);
  }

  @Test
  void keepsTheRuleOrderOfTheParseTree() {
    final List<String> kinds =
        new PythonFormatSyntax().parse("x = 1\n").rules().stream().map(FormatRule::kind).toList();

    assertThat(kinds).startsWith("file_input");
  }
}
