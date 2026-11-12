package dev.ward.analysis.domain.openapi;

import java.util.ArrayList;
import java.util.List;

/** Reads literal ({@code |}) and folded ({@code >}) block scalars. */
final class YamlBlockScalar {

  private static final int DECIMAL = 10;

  private final YamlCursor cursor;
  private final YamlLines lines;

  YamlBlockScalar(final YamlCursor cursor, final YamlLines lines) {
    this.cursor = cursor;
    this.lines = lines;
  }

  /**
   * A block scalar's text and the offset where its last content ends.
   *
   * @param text the decoded text
   * @param end the offset after the last character of the scalar, or of its header when empty
   */
  record Block(String text, int end) {}

  /**
   * Reads the scalar at the cursor, which is on the {@code |} or {@code >} indicator. The content
   * must be indented deeper than {@code parentIndent}.
   */
  Block read(final int parentIndent) {
    final boolean folded = cursor.peek() == '>';
    cursor.advance(1);
    int explicit = 0;
    char chomp = 'c';
    while (cursor.peek() == '+' || cursor.peek() == '-' || Character.isDigit(cursor.peek())) {
      final char c = cursor.peek();
      if (Character.isDigit(c)) {
        explicit = Character.digit(c, DECIMAL);
      } else {
        chomp = c;
      }
      cursor.advance(1);
    }
    if (!lines.skipToLineEnd()) {
      throw cursor.fail("unexpected text after the block scalar header", cursor.pos());
    }
    final int headerEnd = cursor.pos();
    lines.skipBreak();
    final List<String> rows = new ArrayList<>();
    final int[] last = {headerEnd};
    final int indent = contentIndent(parentIndent, explicit);
    if (indent < 0) {
      return new Block("", headerEnd);
    }
    collect(rows, last, indent);
    return new Block(chomped(rows, folded, chomp), last[0]);
  }

  /** The indentation of the content lines, or -1 when there is no content. */
  private int contentIndent(final int parentIndent, final int explicit) {
    if (explicit > 0) {
      return Math.max(parentIndent, 0) + explicit;
    }
    int offset = cursor.pos();
    while (offset < cursor.text().length()) {
      int index = offset;
      while (cursor.charAt(index) == ' ') {
        index++;
      }
      if (!YamlCursor.isBreak(cursor.charAt(index)) && index < cursor.text().length()) {
        return index - offset > parentIndent ? index - offset : -1;
      }
      offset = nextLine(index);
    }
    return -1;
  }

  private int nextLine(final int from) {
    int index = from;
    if (cursor.charAt(index) == '\r' && cursor.charAt(index + 1) == '\n') {
      index++;
    }
    return index + 1;
  }

  private void collect(final List<String> rows, final int[] last, final int indent) {
    while (!cursor.eof()) {
      final int lineStart = cursor.pos();
      int index = lineStart;
      while (cursor.charAt(index) == ' ' && index - lineStart < indent) {
        index++;
      }
      final int end = lineEnd(index);
      final boolean blank =
          cursor.text().substring(index, end).isBlank() && end - lineStart <= indent;
      if (!blank && index - lineStart < indent) {
        return;
      }
      rows.add(blank ? "" : cursor.text().substring(index, end));
      if (!blank) {
        last[0] = end;
      }
      cursor.moveTo(end);
      lines.skipBreak();
    }
  }

  private int lineEnd(final int from) {
    int end = from;
    while (end < cursor.text().length() && !YamlCursor.isBreak(cursor.charAt(end))) {
      end++;
    }
    return end;
  }

  private static String chomped(final List<String> rows, final boolean folded, final char chomp) {
    int count = rows.size();
    while (count > 0 && rows.get(count - 1).isEmpty()) {
      count--;
    }
    final List<String> content = rows.subList(0, count);
    final String body = folded ? fold(content) : String.join("\n", content);
    if (chomp == '-' || count == 0 && chomp != '+') {
      return body;
    }
    final String kept = chomp == '+' ? "\n".repeat(rows.size() - count) : "";
    return count == 0 ? kept : body + "\n" + kept;
  }

  private static String fold(final List<String> rows) {
    final StringBuilder text = new StringBuilder();
    String previous = null;
    int blanks = 0;
    for (final String line : rows) {
      if (line.isEmpty()) {
        blanks++;
      } else {
        text.append(separator(previous, line, blanks)).append(line);
        previous = line;
        blanks = 0;
      }
    }
    return text.toString();
  }

  /** What joins a folded line to the one before it: a space, or the line breaks it keeps. */
  private static String separator(final String previous, final String line, final int blanks) {
    if (previous == null) {
      return "\n".repeat(blanks);
    }
    final boolean indented = indents(previous) || indents(line);
    if (blanks == 0) {
      return indented ? "\n" : " ";
    }
    return "\n".repeat(blanks + (indented ? 1 : 0));
  }

  private static boolean indents(final String line) {
    return line.charAt(0) == ' ' || line.charAt(0) == '\t';
  }
}
