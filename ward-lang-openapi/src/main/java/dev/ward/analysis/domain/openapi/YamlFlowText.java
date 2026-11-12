package dev.ward.analysis.domain.openapi;

/** Scans the plain scalars of the flow context, which end at a flow indicator. */
final class YamlFlowText {

  private static final String INDICATORS = ",[]{}";

  private final YamlCursor cursor;
  private final YamlLines lines;

  YamlFlowText(final YamlCursor cursor, final YamlLines lines) {
    this.cursor = cursor;
    this.lines = lines;
  }

  /** Tells whether the character ends a flow entry or collection, or the text. */
  static boolean isEnd(final char c) {
    return c == ',' || c == ']' || c == '}' || c == '\0';
  }

  /** Reads a plain scalar of the flow context and leaves the cursor at its end. */
  String read() {
    final int start = cursor.pos();
    int end = start;
    while (end < cursor.text().length() && !endsPlain(end)) {
      end++;
    }
    int trimmed = end;
    while (trimmed > start && isSpace(cursor.charAt(trimmed - 1))) {
      trimmed--;
    }
    cursor.moveTo(trimmed);
    if (trimmed == start && !isEnd(cursor.peek())) {
      throw cursor.fail("unexpected character", start);
    }
    return cursor.text().substring(start, trimmed);
  }

  /** Moves past spaces, line breaks and comments, which flow collections may hold anywhere. */
  void skipSpace() {
    while (!cursor.eof()) {
      final char c = cursor.peek();
      if (isSpace(c) || YamlCursor.isBreak(c)) {
        cursor.advance(1);
      } else if (c == '#' && cursor.isBlankAt(cursor.pos() - 1)) {
        while (!lines.atLineEnd()) {
          cursor.advance(1);
        }
      } else {
        return;
      }
    }
  }

  private boolean endsPlain(final int at) {
    final char c = cursor.charAt(at);
    final boolean colon =
        c == ':' && (cursor.isBlankAt(at + 1) || INDICATORS.indexOf(cursor.charAt(at + 1)) >= 0);
    final boolean comment = c == '#' && at > 0 && cursor.isBlankAt(at - 1);
    return INDICATORS.indexOf(c) >= 0 || colon || comment || YamlCursor.isBreak(c);
  }

  private static boolean isSpace(final char c) {
    return c == ' ' || c == '\t';
  }
}
