package dev.ward.analysis.domain.format;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Checks the layout of the if-break document, flat and broken. */
class DocPrinterIfBreakTest {

  private static final int WIDE = 100;
  private static final int FOUR = 4;

  private static String print(final Document doc) {
    return new DocPrinter(new PrintStyle(WIDE, IndentStyle.SPACE, FOUR, EndOfLine.LF)).print(doc);
  }

  @Test
  void ifBreakPrintsItsFlatAlternativeWhenTheGroupStaysFlat() {
    final Document list =
        Document.group(
            Document.concat(
                Document.text("["),
                Document.text("1"),
                Document.ifBreak(Document.text(","), Document.text("")),
                Document.text("]")));

    assertThat(print(list)).isEqualTo("[1]");
  }

  @Test
  void ifBreakPrintsItsBrokenAlternativeWhenTheGroupBreaks() {
    final Document list =
        Document.group(
            Document.concat(
                Document.text("["),
                Document.indent(
                    Document.concat(
                        Document.softLine(),
                        Document.text("1"),
                        Document.ifBreak(Document.text(","), Document.text("")))),
                Document.hardLine(),
                Document.text("]")));

    assertThat(print(list)).isEqualTo("[\n    1,\n]");
  }
}
