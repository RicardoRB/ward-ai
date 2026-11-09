package dev.ward.analysis.domain.rule.check;

/**
 * Scans a %-format string the way pylint's parser does, raising when it is truncated or
 * unsupported.
 */
final class PercentFormatScanner {

  private static final String SUPPORTED = "diouxXeEfFgGcrs%a";
  private static final char PERCENT = '%';
  private static final char OPEN_PAREN = '(';
  private static final char CLOSE_PAREN = ')';
  private static final char DOT = '.';
  private static final String FLAGS = "#0- +";
  private static final String LENGTH = "hlL";
  private static final String DIGITS = "0123456789";

  private PercentFormatScanner() {}

  static void scan(final String format) {
    int index = 0;
    while (index < format.length()) {
      index = format.charAt(index) == PERCENT ? specifier(format, index + 1) : index + 1;
    }
  }

  static int specifier(final String format, final int start) {
    int index = parenthesized(format, start);
    index = skip(format, index, FLAGS);
    index = skip(format, index, DIGITS);
    if (at(format, index) == DOT) {
      index = skip(format, next(format, index), DIGITS);
    }
    if (LENGTH.indexOf(at(format, index)) >= 0) {
      index = next(format, index);
    }
    if (SUPPORTED.indexOf(at(format, index)) < 0) {
      throw new UnsupportedFormat();
    }
    return index + 1;
  }

  /** Skips a mapping key in parentheses, which may nest, and returns the index after it. */
  static int parenthesized(final String format, final int start) {
    if (at(format, start) != OPEN_PAREN) {
      return start;
    }
    int depth = 1;
    int index = start + 1;
    while (depth != 0) {
      index = next(format, index);
      depth += depthChange(at(format, index));
    }
    return next(format, index);
  }

  static int depthChange(final char c) {
    if (c == OPEN_PAREN) {
      return 1;
    }
    return c == CLOSE_PAREN ? -1 : 0;
  }

  static int skip(final String format, final int start, final String chars) {
    int index = start;
    while (index < format.length() && chars.indexOf(format.charAt(index)) >= 0) {
      index++;
    }
    return index;
  }

  static char at(final String format, final int index) {
    if (index >= format.length()) {
      throw new IncompleteFormat();
    }
    return format.charAt(index);
  }

  static int next(final String format, final int index) {
    if (index + 1 >= format.length()) {
      throw new IncompleteFormat();
    }
    return index + 1;
  }

  /** The format ends inside a specifier. */
  static final class IncompleteFormat extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  /** A conversion character outside the supported set. */
  static final class UnsupportedFormat extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }
}
