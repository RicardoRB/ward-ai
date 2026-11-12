package dev.ward.analysis.domain.openapi;

/** Looks ahead on a line to tell a mapping key from a scalar, and finds document markers. */
final class YamlKeys {

  private static final String START = "---";
  private static final String END = "...";
  private static final char SINGLE = '\'';
  private static final char DOUBLE = '"';
  private static final char BACKSLASH = '\\';

  private final YamlCursor cursor;

  YamlKeys(final YamlCursor cursor) {
    this.cursor = cursor;
  }

  /** Tells whether a block sequence entry ({@code -} then a blank) is at {@code offset}. */
  boolean sequenceEntryAt(final int offset) {
    return cursor.charAt(offset) == '-' && cursor.isBlankAt(offset + 1);
  }

  /** Tells whether a document marker starts the line that starts at {@code lineStart}. */
  boolean markerAt(final int lineStart) {
    final String text = cursor.text();
    return text.startsWith(START, lineStart) && cursor.isBlankAt(lineStart + START.length())
        || text.startsWith(END, lineStart) && cursor.isBlankAt(lineStart + END.length());
  }

  /** The offset of the colon that ends a plain key starting at {@code from}, or -1. */
  int plainKeyColon(final int from) {
    int at = from;
    while (at < cursor.text().length() && !YamlCursor.isBreak(cursor.charAt(at))) {
      final char c = cursor.charAt(at);
      if (c == ':' && cursor.isBlankAt(at + 1)) {
        return at;
      }
      if (c == '#' && at > from && cursor.isBlankAt(at - 1)) {
        return -1;
      }
      at++;
    }
    return -1;
  }

  /** The offset of the colon after a quoted key at {@code from}, or -1 when no key follows. */
  int quotedKeyColon(final int from) {
    final char quote = cursor.charAt(from);
    int at = from + 1;
    while (at < cursor.text().length() && !YamlCursor.isBreak(cursor.charAt(at))) {
      final char c = cursor.charAt(at);
      if (quote == DOUBLE && c == BACKSLASH || quote == SINGLE && c == SINGLE && doubled(at)) {
        at++;
      } else if (c == quote) {
        return colonAfter(at + 1);
      }
      at++;
    }
    return -1;
  }

  private boolean doubled(final int at) {
    return cursor.charAt(at + 1) == SINGLE;
  }

  private int colonAfter(final int from) {
    int at = from;
    while (cursor.charAt(at) == ' ' || cursor.charAt(at) == '\t') {
      at++;
    }
    return cursor.charAt(at) == ':' && cursor.isBlankAt(at + 1) ? at : -1;
  }
}
