package dev.ward.analysis.domain.format;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocPrinterTest {

  private static final int WIDE = 100;
  private static final int TEN = 10;
  private static final int EIGHT = 8;
  private static final int FIVE = 5;
  private static final int FOUR = 4;

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
  void printsGroupFlatWhenItFits() {
    assertThat(
            print(
                Document.group(
                    Document.concat(Document.text("a"), Document.line(), Document.text("b"))),
                TEN))
        .isEqualTo("a b");
  }

  @Test
  void breaksGroupWhenItDoesNotFit() {
    assertThat(
            print(
                Document.group(
                    Document.concat(Document.text("aaaa"), Document.line(), Document.text("bbbb"))),
                EIGHT))
        .isEqualTo("aaaa\nbbbb");
  }

  @Test
  void fitsExactlyToTheLineWidth() {
    final Document doc =
        Document.group(Document.concat(Document.text("ab"), Document.line(), Document.text("cd")));

    assertThat(print(doc, FIVE)).isEqualTo("ab cd");
    assertThat(print(doc, FOUR)).isEqualTo("ab\ncd");
  }

  @Test
  void countsTextAfterTheGroupOnTheSameLine() {
    final Document doc =
        Document.concat(
            Document.group(
                Document.concat(Document.text("a"), Document.line(), Document.text("b"))),
            Document.text("cccc"));

    assertThat(print(doc, FIVE)).isEqualTo("a\nbcccc");
  }

  @Test
  void printsInnerGroupFlatInsideBrokenOuterGroup() {
    final Document inner =
        Document.group(Document.concat(Document.text("a,"), Document.line(), Document.text("b")));
    final Document outer =
        Document.group(
            Document.concat(
                Document.text("call("),
                Document.indent(Document.concat(Document.softLine(), inner)),
                Document.softLine(),
                Document.text(")")));

    assertThat(print(outer, EIGHT)).isEqualTo("call(\n    a, b\n)");
  }

  @Test
  void breaksEveryLineOfABrokenGroup() {
    final Document outer =
        Document.group(
            Document.concat(
                Document.text("call("),
                Document.indent(
                    Document.concat(
                        Document.softLine(),
                        Document.text("a,"),
                        Document.line(),
                        Document.text("b"))),
                Document.softLine(),
                Document.text(")")));

    assertThat(print(outer, EIGHT)).isEqualTo("call(\n    a,\n    b\n)");
  }

  @Test
  void forcedLineBreaksEnclosingGroupEvenWhenShort() {
    final Document doc =
        Document.group(
            Document.concat(
                Document.text("a"),
                Document.group(
                    Document.concat(Document.text("b"), Document.line(), Document.text("c"))),
                Document.hardLine(),
                Document.text("d")));

    assertThat(print(doc, WIDE)).isEqualTo("ab c\nd");
  }

  @Test
  void keepsSoftLineEmptyWhenFlat() {
    assertThat(
            print(
                Document.group(
                    Document.concat(Document.text("a"), Document.softLine(), Document.text("b"))),
                WIDE))
        .isEqualTo("ab");
  }
}
