package dev.ward.analysis.domain.openapi;

/** The position in a YAML text and the character-level moves every part of the parser shares. */
final class YamlCursor {

  private final String text;
  private int pos;

  YamlCursor(final YamlSource source) {
    this.text = source.text();
    this.pos = source.start();
  }

  int pos() {
    return pos;
  }

  void moveTo(final int offset) {
    pos = offset;
  }

  void advance(final int count) {
    pos += count;
  }

  boolean eof() {
    return pos >= text.length();
  }

  /** The whole text; offsets are into it. */
  String text() {
    return text;
  }

  /** The character at the position, or NUL at the end of the text. */
  char peek() {
    return charAt(pos);
  }

  /** The character at {@code offset}, or NUL outside the text. */
  char charAt(final int offset) {
    return offset >= 0 && offset < text.length() ? text.charAt(offset) : '\0';
  }

  static boolean isBreak(final char c) {
    return c == '\n' || c == '\r';
  }

  /** Tells whether the character ends a token: a space, tab, line break or the end of the text. */
  boolean isBlankAt(final int offset) {
    final char c = charAt(offset);
    return offset >= text.length() || c == ' ' || c == '\t' || isBreak(c);
  }

  YamlSyntaxException fail(final String message, final int offset) {
    return new YamlSyntaxException(message, offset);
  }
}
