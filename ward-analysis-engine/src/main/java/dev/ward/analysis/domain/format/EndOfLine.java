package dev.ward.analysis.domain.format;

/** The line ending a printed document uses. */
public enum EndOfLine {
  /** A line feed. */
  LF,
  /** A carriage return followed by a line feed. */
  CRLF;

  /**
   * Finds the line ending a source text already uses, for the {@code preserve} setting.
   *
   * @param source the source text
   * @return {@link #CRLF} when its first line ends with a carriage return, otherwise {@link #LF}
   */
  public static EndOfLine detect(final String source) {
    final int newline = source.indexOf('\n');
    final boolean crlf = newline > 0 && source.charAt(newline - 1) == '\r';
    return crlf ? CRLF : LF;
  }
}
