package dev.ward.analysis.frontend.antlr.format.rust;

import dev.ward.analysis.domain.format.Document;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/** The text between two tokens: the spaces on a line, or the line breaks and blank lines. */
final class RustGaps {
  private static final String LF = "\n";

  private final List<RustKind> kinds;
  private final RustSpacing spacing;

  /**
   * Prepares the gaps of one source.
   *
   * @param kinds the kind of every token
   * @param spacing the spacing rules
   */
  RustGaps(final List<RustKind> kinds, final RustSpacing spacing) {
    this.kinds = List.copyOf(kinds);
    this.spacing = spacing;
  }

  /**
   * The source text between two tokens.
   *
   * @param previous the earlier token
   * @param token the later token
   * @return the whitespace between them
   */
  static String between(final Token previous, final Token token) {
    final CharStream stream = previous.getInputStream();
    final int first = previous.getStopIndex() + 1;
    final int last = token.getStartIndex() - 1;
    return first > last ? "" : stream.getText(Interval.of(first, last));
  }

  /**
   * The gap between two tokens on one line.
   *
   * @param index the position of the later token
   * @param between the source whitespace between the two
   * @return one space or none
   */
  Document inline(final int index, final String between) {
    return Document.text(spacing.gap(index, between));
  }

  /**
   * The gap between two tokens on different lines: the line break and the blank lines to keep.
   * Blank lines go after an opening bracket and before a closing one.
   *
   * @param newlines the number of line breaks in the source
   * @param index the position of the later token
   * @param maxBlankLines the most blank lines kept in a row
   * @return the document of the break
   */
  Document lines(final int newlines, final int index, final int maxBlankLines) {
    final RustKind previous = kinds.get(index - 1);
    final RustKind kind = kinds.get(index);
    final boolean tight =
        previous == RustKind.OPEN
            || previous == RustKind.LEFT_BRACE
            || kind == RustKind.CLOSE
            || kind == RustKind.RIGHT_BRACE;
    final int blank = Math.min(newlines - 1, tight ? 0 : maxBlankLines);
    final List<Document> parts = new ArrayList<>();
    for (int line = 0; line < blank; line++) {
      parts.add(Document.text(LF));
    }
    parts.add(Document.hardLine());
    return new Document.Concat(parts);
  }
}
