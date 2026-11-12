package dev.ward.analysis.domain.openapi;

/** Stops the parser at the first problem; it never leaves the parser. */
final class YamlSyntaxException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int offset;

  YamlSyntaxException(final String message, final int offset) {
    super(message);
    this.offset = offset;
  }

  int offset() {
    return offset;
  }
}
