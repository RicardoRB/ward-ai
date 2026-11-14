package dev.ward.analysis.domain.android;

/**
 * A problem in the XML text; {@link XmlParser} turns it into a diagnostic and never lets it out.
 */
final class XmlSyntaxException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int offset;

  XmlSyntaxException(final String message, final int offset) {
    super(message);
    this.offset = offset;
  }

  XmlSyntaxException(final String message, final int offset, final Throwable cause) {
    super(message, cause);
    this.offset = offset;
  }

  int offset() {
    return offset;
  }
}
