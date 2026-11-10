package dev.ward.domain.format;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.format.FormatLayout.ByteOrderMark;
import dev.ward.domain.format.FormatLayout.IndentStyle;
import dev.ward.domain.format.FormatLayout.LineEnding;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class FormatLayoutsTest {

  private static final String SOURCE = "class A {}\n";
  private static final int WIDTH = 100;
  private static final int INDENT = 4;
  private static final int LINE_AFTER_END = 3;

  @Test
  void keepsTheFileLineEndingWhenTheLayoutLeavesItUnset() {
    assertThat(FormatLayouts.apply(SOURCE, layout(Optional.empty(), true), LineEnding.CRLF))
        .isEqualTo("class A {}\r\n");
  }

  @Test
  void aLayoutLineEndingWinsOverTheFile() {
    assertThat(
            FormatLayouts.apply(SOURCE, layout(Optional.of(LineEnding.LF), true), LineEnding.CRLF))
        .isEqualTo(SOURCE);
  }

  @Test
  void addsOrRemovesTheFinalNewline() {
    assertThat(FormatLayouts.apply("class A {}", layout(Optional.empty(), true), LineEnding.LF))
        .isEqualTo(SOURCE);
    assertThat(FormatLayouts.apply(SOURCE, layout(Optional.empty(), false), LineEnding.LF))
        .isEqualTo("class A {}");
  }

  @Test
  void aMarkIsRequiredByTheLayoutOrKeptFromTheFile() {
    assertThat(FormatLayouts.byteOrderMark(layout(Optional.empty(), true), false)).isFalse();
    assertThat(FormatLayouts.byteOrderMark(layout(Optional.empty(), true), true)).isTrue();
    assertThat(FormatLayouts.byteOrderMark(bomLayout(), false)).isTrue();
  }

  @Test
  void readsTheFirstLineEndingOfAFile() {
    assertThat(FormatLayouts.lineEndingOf("a\r\nb\n")).isEqualTo(LineEnding.CRLF);
    assertThat(FormatLayouts.lineEndingOf("a\nb\r\n")).isEqualTo(LineEnding.LF);
    assertThat(FormatLayouts.lineEndingOf("no newline")).isEqualTo(LineEnding.LF);
  }

  @Test
  void reportsTheFirstDifferingLineIgnoringLineEndings() {
    assertThat(FormatLayouts.firstDifferentLine("a\r\nb\r\n", "a\nc\n")).isEqualTo(2);
    assertThat(FormatLayouts.firstDifferentLine("a\nb", "a\nb\n")).isEqualTo(LINE_AFTER_END);
  }

  private static FormatLayout layout(
      final Optional<LineEnding> ending, final boolean finalNewline) {
    return new FormatLayout(
        OptionalInt.of(WIDTH),
        IndentStyle.SPACES,
        INDENT,
        INDENT,
        ending,
        finalNewline,
        ByteOrderMark.PRESERVE);
  }

  private static FormatLayout bomLayout() {
    return new FormatLayout(
        OptionalInt.of(WIDTH),
        IndentStyle.SPACES,
        INDENT,
        INDENT,
        Optional.empty(),
        true,
        ByteOrderMark.REQUIRED);
  }
}
