package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.Span;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/** Converts ANTLR rule contexts and tokens to the 1-based {@link Span} of the shared shape. */
final class SyntaxSpans {

  private SyntaxSpans() {}

  /** Returns the span from the first to the last token of {@code context}. */
  static Span span(final ParserRuleContext context) {
    final Token start = context.getStart();
    final Token stop = context.getStop();
    if (stop == null || stop.getStopIndex() < start.getStartIndex()) {
      return zeroWidth(start);
    }
    final int startOffset = Math.max(0, start.getStartIndex());
    final int endOffset = Math.max(startOffset, stop.getStopIndex() + 1);
    final int endLine = stop.getLine();
    final int endColumn = stop.getCharPositionInLine() + width(stop);
    if (endLine < start.getLine()
        || endLine == start.getLine() && endColumn < start.getCharPositionInLine() + 1) {
      return zeroWidth(start);
    }
    return new Span(
        start.getLine(),
        start.getCharPositionInLine() + 1,
        endLine,
        endColumn,
        startOffset,
        endOffset);
  }

  /** Returns the span of a single token. */
  static Span token(final Token token) {
    final int startOffset = Math.max(0, token.getStartIndex());
    final int endOffset = Math.max(startOffset, token.getStopIndex() + 1);
    return new Span(
        token.getLine(),
        token.getCharPositionInLine() + 1,
        token.getLine(),
        token.getCharPositionInLine() + width(token),
        startOffset,
        endOffset);
  }

  /** Returns the span of a comment token, which may cover several lines. */
  static Span comment(final Token token) {
    final String text = token.getText();
    final int newlines = (int) text.chars().filter(c -> c == '\n').count();
    final int endLine = token.getLine() + newlines;
    final int endColumn =
        newlines == 0
            ? token.getCharPositionInLine() + text.length()
            : text.length() - text.lastIndexOf('\n') - 1;
    return new Span(
        token.getLine(),
        token.getCharPositionInLine() + 1,
        endLine,
        endColumn,
        Math.max(0, token.getStartIndex()),
        Math.max(0, token.getStopIndex() + 1));
  }

  private static Span zeroWidth(final Token start) {
    final int startOffset = Math.max(0, start.getStartIndex());
    final int column = start.getCharPositionInLine() + 1;
    return new Span(start.getLine(), column, start.getLine(), column, startOffset, startOffset);
  }

  private static int width(final Token token) {
    return Math.max(1, token.getStopIndex() - token.getStartIndex() + 1);
  }
}
