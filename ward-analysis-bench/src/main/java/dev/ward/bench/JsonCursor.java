package dev.ward.bench;

/** The reading position in a JSON text, with the checks that the reader needs. */
final class JsonCursor {

  private final String text;
  private int position;

  JsonCursor(final String text) {
    this.text = text;
  }

  /** Skips white space, then reports whether the text has a character left. */
  boolean atEnd() {
    skipSpace();
    return position >= text.length();
  }

  /** Skips white space, then returns the next character, or the fallback at the end. */
  char peekOr(final char fallback) {
    return atEnd() ? fallback : text.charAt(position);
  }

  /** Returns the next character and moves past it; fails at the end of the text. */
  char next() {
    if (position >= text.length()) {
      throw error("unexpected end of document");
    }
    final char c = text.charAt(position);
    position++;
    return c;
  }

  /** Returns the position, so a caller can slice the text from it. */
  int position() {
    return position;
  }

  /** Returns the text from a position up to the current one. */
  String slice(final int start) {
    return text.substring(start, position);
  }

  /** Moves past the characters of the set and returns how many there were. */
  int skipWhile(final String set) {
    final int start = position;
    while (position < text.length() && set.indexOf(text.charAt(position)) >= 0) {
      position++;
    }
    return position - start;
  }

  /** Skips white space, then moves past the character when it is the expected one. */
  boolean consume(final char expected) {
    skipSpace();
    if (position < text.length() && text.charAt(position) == expected) {
      position++;
      return true;
    }
    return false;
  }

  /** Skips white space, then moves past the expected character, or fails. */
  void expect(final char expected) {
    if (!consume(expected)) {
      throw error("expected '" + expected + "'");
    }
  }

  /** Builds the error for a malformed document, at the current position. */
  IllegalArgumentException error(final String problem) {
    return new IllegalArgumentException("Invalid JSON at offset " + position + ": " + problem);
  }

  private void skipSpace() {
    while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
      position++;
    }
  }
}
