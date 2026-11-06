package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;

/** Computes spans inside a token. */
final class PythonSpans {

  private static final char NEWLINE = '\n';

  private PythonSpans() {}

  /** The span of {@code from..to} of the token text, inside the token's own span. */
  static Span within(final Span token, final String text, final int from, final int to) {
    final int[] start = position(token, text, from);
    final int[] end = position(token, text, to);
    return new Span(
        start[0], start[1], end[0], end[1], token.startOffset() + from, token.startOffset() + to);
  }

  /** The line and column of the character at {@code index} of the token text. */
  private static int[] position(final Span token, final String text, final int index) {
    int line = token.startLine();
    int column = token.startColumn();
    for (int position = 0; position < index; position++) {
      if (text.charAt(position) == NEWLINE) {
        line++;
        column = 1;
      } else {
        column++;
      }
    }
    return new int[] {line, column};
  }
}
