package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.Span;

/** Spans of text for tests. */
final class Spans {
  private Spans() {}

  static Span of(final String text, final String token, final boolean last) {
    final int offset = last ? text.lastIndexOf(token) : text.indexOf(token);
    if (offset < 0) {
      throw new IllegalArgumentException("no " + token);
    }
    return at(text, offset, offset + token.length());
  }

  static Span at(final String text, final int start, final int end) {
    final int line = (int) text.substring(0, start).chars().filter(c -> c == '\n').count() + 1;
    final int column = start - text.lastIndexOf('\n', start - 1);
    final int endLine = (int) text.substring(0, end).chars().filter(c -> c == '\n').count() + 1;
    final int endColumn = end - text.lastIndexOf('\n', end - 1);
    return new Span(line, column, endLine, endColumn, start, end);
  }
}
