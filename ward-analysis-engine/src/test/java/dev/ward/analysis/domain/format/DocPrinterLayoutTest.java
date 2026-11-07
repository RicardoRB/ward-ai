package dev.ward.analysis.domain.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DocPrinterLayoutTest {

  private static final int WIDE = 100;
  private static final int FOUR = 4;
  private static final int TWO = 2;

  private static String print(final Document doc, final int width) {
    return print(doc, width, IndentStyle.SPACE, FOUR, EndOfLine.LF);
  }

  private static String print(
      final Document doc,
      final int width,
      final IndentStyle style,
      final int indentWidth,
      final EndOfLine endOfLine) {
    return new DocPrinter(new PrintStyle(width, style, indentWidth, endOfLine)).print(doc);
  }

  @Test
  void indentsBreaksByOneLevelOfSpaces() {
    final Document doc =
        Document.concat(
            Document.text("x"),
            Document.indent(Document.concat(Document.hardLine(), Document.text("y"))));

    assertThat(print(doc, WIDE)).isEqualTo("x\n    y");
  }

  @Test
  void indentsNestedLevelsByIndentWidth() {
    final Document doc =
        Document.concat(
            Document.text("x"),
            Document.indent(
                Document.indent(Document.concat(Document.hardLine(), Document.text("z")))));

    assertThat(print(doc, WIDE, IndentStyle.SPACE, TWO, EndOfLine.LF)).isEqualTo("x\n    z");
  }

  @Test
  void indentsWithOneTabPerLevel() {
    final Document doc =
        Document.concat(
            Document.text("x"),
            Document.indent(
                Document.indent(Document.concat(Document.hardLine(), Document.text("z")))));

    assertThat(print(doc, WIDE, IndentStyle.TAB, FOUR, EndOfLine.LF)).isEqualTo("x\n\t\tz");
  }

  @Test
  void writesLineFeedAtEveryBreak() {
    assertThat(
            print(
                Document.concat(Document.text("a"), Document.hardLine(), Document.text("b")),
                WIDE,
                IndentStyle.SPACE,
                FOUR,
                EndOfLine.LF))
        .isEqualTo("a\nb");
  }

  @Test
  void writesCarriageReturnLineFeedAtEveryBreak() {
    assertThat(
            print(
                Document.concat(Document.text("a"), Document.hardLine(), Document.text("b")),
                WIDE,
                IndentStyle.SPACE,
                FOUR,
                EndOfLine.CRLF))
        .isEqualTo("a\r\nb");
  }

  @Test
  void writesBlockWithNestedIndentation() {
    final Document block =
        Document.group(
            Document.concat(
                Document.text("{"),
                Document.indent(Document.concat(Document.hardLine(), Document.text("x"))),
                Document.hardLine(),
                Document.text("}")));

    assertThat(print(block, WIDE)).isEqualTo("{\n    x\n}");
  }

  @Test
  void detectsLineEndingOfSource() {
    assertThat(EndOfLine.detect("a\r\nb\n")).isEqualTo(EndOfLine.CRLF);
    assertThat(EndOfLine.detect("a\nb\r\n")).isEqualTo(EndOfLine.LF);
    assertThat(EndOfLine.detect("single line")).isEqualTo(EndOfLine.LF);
  }

  @Test
  void rejectsNonPositiveLineWidth() {
    assertThatThrownBy(() -> new PrintStyle(0, IndentStyle.SPACE, FOUR, EndOfLine.LF))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
