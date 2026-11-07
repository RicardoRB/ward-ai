package dev.ward.analysis.frontend.antlr.python;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.atn.LexerATNSimulator;

/**
 * Scans the body of an f-string or t-string after the lexer has matched its opening quotes. The
 * body is consumed through the lexer's interpreter, so its line count stays right.
 */
final class PythonTemplateString {

  private static final int TRIPLE_QUOTES = 3;
  private static final int LOOKAHEAD_THIRD = 3;
  private static final int ESCAPE_LENGTH = 2;
  private static final int BACKSLASH = '\\';
  private static final int OPEN_BRACE = '{';
  private static final int CLOSE_BRACE = '}';
  private static final int OPEN_PAREN = '(';
  private static final int CLOSE_PAREN = ')';
  private static final int OPEN_BRACKET = '[';
  private static final int CLOSE_BRACKET = ']';
  private static final int SINGLE_QUOTE = '\'';
  private static final int DOUBLE_QUOTE = '"';

  private final CharStream input;
  private final LexerATNSimulator interpreter;

  /**
   * Binds the scanner to the input the lexer reads.
   *
   * @param input the character stream of the lexer
   * @param interpreter the lexer's interpreter, which consumes the characters
   */
  PythonTemplateString(final CharStream input, final LexerATNSimulator interpreter) {
    this.input = input;
    this.interpreter = interpreter;
  }

  /** Consumes the body of an f-string or t-string whose prefix and opening quotes were matched. */
  void scan(final String opening) {
    final int quote = opening.charAt(opening.length() - 1);
    scanTemplateBody(quote, isTripleQuoted(opening, quote));
  }

  private static boolean isTripleQuoted(final String opening, final int quote) {
    return opening.length() > TRIPLE_QUOTES
        && opening.endsWith(String.valueOf((char) quote).repeat(TRIPLE_QUOTES));
  }

  private void scanTemplateBody(final int quote, final boolean triple) {
    int next = input.LA(1);
    while (next != IntStream.EOF
        && !closesAt(next, quote, triple)
        && (triple || !isLineBreak(next))) {
      if (next == BACKSLASH) {
        consumeChars(ESCAPE_LENGTH);
      } else if (next == OPEN_BRACE) {
        scanBrace();
      } else {
        consumeChars(1);
      }
      next = input.LA(1);
    }
    if (closesAt(next, quote, triple)) {
      consumeChars(quoteLength(triple));
    }
  }

  /**
   * Consumes an escaped brace, or a replacement field whose opening brace is the next character.
   */
  private void scanBrace() {
    consumeChars(1);
    if (input.LA(1) == OPEN_BRACE) {
      consumeChars(1);
    } else {
      scanReplacementField();
    }
  }

  private void scanReplacementField() {
    int depth = 0;
    int next = input.LA(1);
    while (next != IntStream.EOF && (depth > 0 || next != CLOSE_BRACE)) {
      if (next == SINGLE_QUOTE || next == DOUBLE_QUOTE) {
        scanNestedString(next);
      } else {
        depth = nestingDepth(next, depth);
        consumeChars(1);
      }
      next = input.LA(1);
    }
    consumeChars(1);
  }

  private static int nestingDepth(final int next, final int depth) {
    if (next == OPEN_BRACE || next == OPEN_PAREN || next == OPEN_BRACKET) {
      return depth + 1;
    }
    if (next == CLOSE_BRACE || next == CLOSE_PAREN || next == CLOSE_BRACKET) {
      return Math.max(0, depth - 1);
    }
    return depth;
  }

  /** Consumes a string literal inside a replacement field. */
  private void scanNestedString(final int quote) {
    final boolean triple = input.LA(2) == quote && input.LA(LOOKAHEAD_THIRD) == quote;
    consumeChars(quoteLength(triple));
    int next = input.LA(1);
    while (next != IntStream.EOF
        && !closesAt(next, quote, triple)
        && (triple || !isLineBreak(next))) {
      consumeChars(next == BACKSLASH ? ESCAPE_LENGTH : 1);
      next = input.LA(1);
    }
    if (closesAt(next, quote, triple)) {
      consumeChars(quoteLength(triple));
    }
  }

  /**
   * Whether the character at the cursor, {@code next}, closes a string opened with {@code quote}.
   */
  private boolean closesAt(final int next, final int quote, final boolean triple) {
    return next == quote && (!triple || input.LA(2) == quote && input.LA(LOOKAHEAD_THIRD) == quote);
  }

  private static boolean isLineBreak(final int next) {
    return next == '\n' || next == '\r';
  }

  private static int quoteLength(final boolean triple) {
    return triple ? TRIPLE_QUOTES : 1;
  }

  private void consumeChars(final int count) {
    for (int index = 0; index < count && input.LA(1) != IntStream.EOF; index++) {
      interpreter.consume(input);
    }
  }
}
