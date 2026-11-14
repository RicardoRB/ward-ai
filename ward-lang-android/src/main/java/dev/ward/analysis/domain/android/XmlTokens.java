package dev.ward.analysis.domain.android;

/** The small reads of XML text that move an {@link XmlCursor}: names, white space, terminators. */
final class XmlTokens {

  private XmlTokens() {}

  /** Reads a name, such as {@code android:name} or {@code service}. */
  static String name(final XmlCursor cursor) {
    final int start = cursor.position();
    while (!cursor.atEnd() && isNameChar(cursor.text().charAt(cursor.position()))) {
      cursor.advance(1);
    }
    if (cursor.position() == start) {
      throw cursor.problem("expected a name");
    }
    return cursor.text().substring(start, cursor.position());
  }

  private static boolean isNameChar(final char value) {
    return Character.isLetterOrDigit(value)
        || value == '_'
        || value == ':'
        || value == '.'
        || value == '-';
  }

  /** Moves past the character {@code expected}, or fails. */
  static void expect(final XmlCursor cursor, final char expected) {
    if (!cursor.at(expected)) {
      throw cursor.problem("expected '" + expected + "'");
    }
    cursor.advance(1);
  }

  static void skipSpace(final XmlCursor cursor) {
    while (!cursor.atEnd() && Character.isWhitespace(cursor.text().charAt(cursor.position()))) {
      cursor.advance(1);
    }
  }

  /** Moves past the next {@code terminator}, or fails with {@code message}. */
  static void skipPast(final XmlCursor cursor, final String terminator, final String message) {
    final int end = cursor.text().indexOf(terminator, cursor.position());
    if (end < 0) {
      throw cursor.problem(message);
    }
    cursor.moveTo(end + terminator.length());
  }
}
