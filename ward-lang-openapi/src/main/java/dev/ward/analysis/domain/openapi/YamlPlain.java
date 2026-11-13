package dev.ward.analysis.domain.openapi;

import dev.ward.analysis.domain.ir.SyntaxNode;

/** Reads plain block scalars, which can continue over several lines. */
final class YamlPlain {

  private final YamlCursor cursor;
  private final YamlLines lines;
  private final YamlKeys keys;

  YamlPlain(final YamlCursor cursor, final YamlLines lines, final YamlKeys keys) {
    this.cursor = cursor;
    this.lines = lines;
    this.keys = keys;
  }

  /** Reads the plain scalar at the cursor; later lines belong to it if indented past the parent. */
  SyntaxNode read(final int parentIndent) {
    final int start = cursor.pos();
    final StringBuilder text = new StringBuilder();
    int end = lineEnd(start);
    text.append(cursor.text(), start, end);
    if (continues(end)) {
      end = continuation(text, end, parentIndent);
    }
    cursor.moveTo(end);
    final String value = text.toString();
    return YamlNodes.scalar(YamlScalars.kindOf(value), lines.span(start, end), value);
  }

  /** Tells whether more text can follow a plain scalar that ended at {@code end}. */
  private boolean continues(final int end) {
    final int next = skipIndent(end);
    return next < cursor.text().length() && cursor.charAt(next) != '#';
  }

  private int skipIndent(final int from) {
    int at = from;
    while (cursor.charAt(at) == ' ' || cursor.charAt(at) == '\t') {
      at++;
    }
    return at;
  }

  /** Folds the lines after the first into {@code text}; returns where the scalar ends. */
  private int continuation(final StringBuilder text, final int firstEnd, final int parentIndent) {
    int end = firstEnd;
    int probe = firstEnd;
    int blanks = 0;
    while (true) {
      final int lineStart = nextLine(probe);
      final int content = skipIndent(lineStart);
      if (content >= cursor.text().length()) {
        return end;
      }
      if (YamlCursor.isBreak(cursor.charAt(content))) {
        blanks++;
        probe = content;
        continue;
      }
      if (endsPlain(lineStart, content, parentIndent)) {
        return end;
      }
      if (keys.plainKeyColon(content) >= 0) {
        throw cursor.fail("a mapping is not allowed inside a multi-line value", content);
      }
      end = lineEnd(content);
      text.append(blanks == 0 ? " " : "\n".repeat(blanks)).append(cursor.text(), content, end);
      blanks = 0;
      probe = end;
      if (!continues(end)) {
        return end;
      }
    }
  }

  private boolean endsPlain(final int lineStart, final int content, final int parentIndent) {
    return content - lineStart <= parentIndent
        || cursor.charAt(content) == '#'
        || keys.markerAt(lineStart);
  }

  /** The start of the line after the one that {@code from} is on. */
  private int nextLine(final int from) {
    int at = from;
    while (at < cursor.text().length() && !YamlCursor.isBreak(cursor.charAt(at))) {
      at++;
    }
    return cursor.charAt(at) == '\r' && cursor.charAt(at + 1) == '\n' ? at + 2 : at + 1;
  }

  /** The end of the plain text on the line from {@code from}: before a comment or a break. */
  private int lineEnd(final int from) {
    int end = from;
    int last = from;
    while (end < cursor.text().length() && !YamlCursor.isBreak(cursor.charAt(end))) {
      if (cursor.charAt(end) == '#' && end > from && cursor.isBlankAt(end - 1)) {
        return last;
      }
      if (cursor.charAt(end) != ' ' && cursor.charAt(end) != '\t') {
        last = end + 1;
      }
      end++;
    }
    return last;
  }
}
