package dev.ward.domain.format;

import dev.ward.domain.format.FormatLayout.ByteOrderMark;
import dev.ward.domain.format.FormatLayout.LineEnding;

/**
 * Applies a file layout to a formatter's output, which always uses {@code \n} and one final
 * newline. The layout only changes line endings and the final newline; the byte-order mark is
 * decided by {@link #byteOrderMark}.
 */
public final class FormatLayouts {
  private static final String LF = "\n";
  private static final String CRLF = "\r\n";
  private static final int FIRST_LINE = 1;

  private FormatLayouts() {}

  /**
   * Applies the line ending and the final newline of a layout.
   *
   * @param formatted the formatter's output, with {@code \n} line endings
   * @param layout the file layout
   * @param original the line ending of the file before formatting, used when the layout keeps it
   * @return the text with the layout applied
   */
  public static String apply(
      final String formatted, final FormatLayout layout, final LineEnding original) {
    final String body = finalNewline(formatted, layout.insertFinalNewline());
    final LineEnding ending = layout.lineEnding().orElse(original);
    return ending == LineEnding.CRLF ? body.replace(LF, CRLF) : body;
  }

  /**
   * Decides whether the output starts with a byte-order mark: a layout that requires one adds it,
   * and otherwise the file keeps the mark it had.
   *
   * @param layout the file layout
   * @param original whether the file had a mark before formatting
   * @return whether the output starts with a mark
   */
  public static boolean byteOrderMark(final FormatLayout layout, final boolean original) {
    return layout.byteOrderMark() == ByteOrderMark.REQUIRED || original;
  }

  /**
   * Returns the line ending a file uses: the first one it has, or LF when it has none.
   *
   * @param text the file text
   * @return the line ending
   */
  public static LineEnding lineEndingOf(final String text) {
    final int newline = text.indexOf('\n');
    return newline > 0 && text.charAt(newline - 1) == '\r' ? LineEnding.CRLF : LineEnding.LF;
  }

  /**
   * Returns the first line, counted from one, where two texts differ, ignoring line-ending
   * differences. When one text is a prefix of the other, the line after the shared lines is
   * returned.
   *
   * @param before the text before formatting
   * @param after the text after formatting
   * @return the first differing line
   */
  public static int firstDifferentLine(final String before, final String after) {
    final String[] first = before.replace(CRLF, LF).split(LF, -1);
    final String[] second = after.replace(CRLF, LF).split(LF, -1);
    final int shared = Math.min(first.length, second.length);
    for (int i = 0; i < shared; i++) {
      if (!first[i].equals(second[i])) {
        return i + FIRST_LINE;
      }
    }
    return shared + FIRST_LINE;
  }

  private static String finalNewline(final String text, final boolean insert) {
    if (text.isEmpty()) {
      return text;
    }
    final boolean ends = text.endsWith(LF);
    if (insert && !ends) {
      return text + LF;
    }
    if (!insert && ends) {
      return text.substring(0, text.length() - 1);
    }
    return text;
  }
}
