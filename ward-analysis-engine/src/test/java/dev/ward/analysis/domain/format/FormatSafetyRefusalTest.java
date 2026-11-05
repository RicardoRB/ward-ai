package dev.ward.analysis.domain.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FormatSafetyRefusalTest {

  private static final FormatOptions NO_OPTIONS = new FormatOptions(Map.of());
  private static final int LINE_TWO = 2;
  private static final int LINE_THREE = 3;
  private static final int LINE_FOUR = 4;
  private final FormatSafety safety = new FormatSafety(new StubFormatSyntax());

  private FormatResult run(final String source, final StubFormatter formatter) {
    return safety.formatSafely(formatter, NO_OPTIONS, source);
  }

  @Test
  void refusesInputWithSyntaxError() {
    final StubFormatter formatter =
        FormatSafetyTest.formatter(s -> new FormatResult.Formatted("x"));

    assertThat(run("a\n@@ b", formatter))
        .isEqualTo(new FormatResult.Refused("input has a syntax error", LINE_TWO));
  }

  @Test
  void refusesOutputWithSyntaxError() {
    final StubFormatter formatter =
        FormatSafetyTest.formatter(s -> new FormatResult.Formatted("a @@"));

    assertThat(run("a", formatter))
        .isEqualTo(new FormatResult.Refused("formatted output has a syntax error", 1));
  }

  @Test
  void refusesLostToken() {
    final StubFormatter formatter =
        FormatSafetyTest.formatter(s -> new FormatResult.Formatted("a"));

    assertThat(run("a b", formatter))
        .isEqualTo(new FormatResult.Refused("formatting changed a token", 1));
  }

  @Test
  void refusesChangedToken() {
    final StubFormatter formatter =
        FormatSafetyTest.formatter(s -> new FormatResult.Formatted("a c"));

    assertThat(run("a b", formatter))
        .isEqualTo(new FormatResult.Refused("formatting changed a token", 1));
  }

  @Test
  void refusesLineBreakThatChangesTheSyntaxTree() {
    final StubFormatter formatter =
        FormatSafetyTest.formatter(s -> new FormatResult.Formatted("a\nb"));

    assertThat(run("a b", formatter))
        .isEqualTo(new FormatResult.Refused("formatting changed the syntax tree", LINE_TWO));
  }

  @Test
  void refusesOutputThatFormatsToDifferentText() {
    final StubFormatter formatter =
        FormatSafetyTest.formatter(s -> new FormatResult.Formatted(s + " "));

    assertThat(run("a", formatter))
        .isEqualTo(new FormatResult.Refused("formatting is not stable", 1));
  }

  @Test
  void refusesOutputThatCannotBeFormattedAgain() {
    final StubFormatter formatter =
        FormatSafetyTest.formatter(
            s ->
                "a".equals(s)
                    ? new FormatResult.Formatted("a ")
                    : new FormatResult.Refused("boom", LINE_THREE));

    assertThat(run("a", formatter))
        .isEqualTo(
            new FormatResult.Refused(
                "formatted output cannot be formatted again: boom", LINE_THREE));
  }

  @Test
  void passesRefusalOfTheFormatterThrough() {
    final StubFormatter formatter =
        FormatSafetyTest.formatter(s -> new FormatResult.Refused("no parse", LINE_FOUR));

    assertThat(run("a", formatter)).isEqualTo(new FormatResult.Refused("no parse", LINE_FOUR));
  }
}
