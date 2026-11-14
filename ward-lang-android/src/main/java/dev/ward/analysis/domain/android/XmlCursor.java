package dev.ward.analysis.domain.android;

/** A position in an XML text and the reads that move it. */
final class XmlCursor {

  private final String text;
  private int position;

  XmlCursor(final String text) {
    this.text = text;
  }

  String text() {
    return text;
  }

  int position() {
    return position;
  }

  void moveTo(final int offset) {
    position = offset;
  }

  void advance(final int count) {
    position += count;
  }

  boolean atEnd() {
    return position >= text.length();
  }

  boolean startsWith(final String prefix) {
    return text.startsWith(prefix, position);
  }

  boolean at(final char expected) {
    return !atEnd() && text.charAt(position) == expected;
  }

  XmlSyntaxException problem(final String message) {
    return new XmlSyntaxException(message, position);
  }
}
