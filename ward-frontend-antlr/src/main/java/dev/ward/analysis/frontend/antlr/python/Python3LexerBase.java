package dev.ward.analysis.frontend.antlr.python;

import dev.ward.analysis.frontend.antlr.WardLexerBase;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Token;

/** Lexer state and helpers used by the Python grammar's actions and predicates. */
abstract class Python3LexerBase extends WardLexerBase {
  private static final int TRIPLE_QUOTES = 3;
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

  // A queue where extra tokens are pushed on (see the NEWLINE lexer rule).
  private List<Token> tokens = new LinkedList<>();
  // The stack that keeps track of the indentation level.
  private Deque<Integer> indents = new ArrayDeque<>();
  // The amount of opened braces, brackets and parenthesis.
  private int opened;
  // The most recently produced token on the default channel.
  private Optional<Token> lastToken = Optional.empty();

  protected Python3LexerBase(final CharStream input) {
    super(input);
  }

  @Override
  public void emit(final Token t) {
    super.setToken(t);
    tokens.add(t);
  }

  @Override
  public Token nextToken() {
    // Check if the end-of-file is ahead and there are still some DEDENTS expected.
    if (_input.LA(1) == EOF && !this.indents.isEmpty()) {
      // Remove any trailing EOF tokens from our buffer.
      for (int i = tokens.size() - 1; i >= 0; i--) {
        if (tokens.get(i).getType() == EOF) {
          tokens.remove(i);
        }
      }

      // First emit an extra line break that serves as the end of the statement.
      this.emit(commonToken(Python3Lexer.NEWLINE, "\n"));

      // Now emit as much DEDENT tokens as needed.
      while (!indents.isEmpty()) {
        this.emit(createDedent());
        indents.pop();
      }

      // Put the EOF back on the token stream.
      this.emit(commonToken(EOF, "<EOF>"));
    }

    final Token next = super.nextToken();

    if (next.getChannel() == Token.DEFAULT_CHANNEL) {
      // Keep track of the last token on the default channel.
      this.lastToken = Optional.of(next);
    }

    return tokens.isEmpty() ? next : tokens.remove(0);
  }

  private Token createDedent() {
    final CommonToken dedent = commonToken(Python3Lexer.DEDENT, "");
    dedent.setLine(this.lastToken.orElseThrow().getLine());
    return dedent;
  }

  private CommonToken commonToken(final int type, final String text) {
    final int stop = this.getCharIndex() - 1;
    final int start = text.isEmpty() ? stop : stop - text.length() + 1;
    return new CommonToken(this._tokenFactorySourcePair, type, DEFAULT_TOKEN_CHANNEL, start, stop);
  }

  boolean atStartOfInput() {
    return super.getCharPositionInLine() == 0 && super.getLine() == 1;
  }

  void openBrace() {
    this.opened++;
  }

  void closeBrace() {
    this.opened--;
  }

  void onNewLine() {
    final String newLine = getText().replaceAll("[^\r\n\f]+", "");
    final String spaces = getText().replaceAll("[\r\n\f]+", "");

    // Strip newlines inside open clauses except if we are near EOF. We keep NEWLINEs near EOF to
    // satisfy the final newline needed by the single_put rule used by the REPL.
    final int next = _input.LA(1);
    final int nextnext = _input.LA(2);
    if (opened > 0
        || (nextnext != -1 && (next == '\r' || next == '\n' || next == '\f' || next == '#'))) {
      // If we're inside a list or on a blank line, ignore all indents,
      // dedents and line breaks.
      skip();
    } else {
      emit(commonToken(Python3Lexer.NEWLINE, newLine));
      emitIndentChange(spaces);
    }
  }

  // Emits INDENT or DEDENT tokens when the indentation of a new line changes.
  private void emitIndentChange(final String spaces) {
    final int indent = PythonIndentation.getIndentationCount(spaces);
    final int previous = indents.isEmpty() ? 0 : indents.peek();
    if (indent == previous) {
      // skip indents of the same size as the present indent-size
      skip();
    } else if (indent > previous) {
      indents.push(indent);
      emit(commonToken(Python3Lexer.INDENT, spaces));
    } else {
      // Possibly emit more than 1 DEDENT token.
      while (!indents.isEmpty() && indents.peek() > indent) {
        this.emit(createDedent());
        indents.pop();
      }
    }
  }

  /**
   * Consumes the body of an f-string or t-string whose prefix and opening quotes were just matched.
   * A replacement field is scanned as code, so it may reuse the enclosing quote (PEP 701).
   */
  void scanTemplateString() {
    final String opening = getText();
    final int quote = opening.charAt(opening.length() - 1);
    scanTemplateBody(quote, isTripleQuoted(opening, quote));
  }

  private static boolean isTripleQuoted(final String opening, final int quote) {
    return opening.length() > TRIPLE_QUOTES
        && opening.endsWith(String.valueOf((char) quote).repeat(TRIPLE_QUOTES));
  }

  private void scanTemplateBody(final int quote, final boolean triple) {
    int next = _input.LA(1);
    while (next != IntStream.EOF && !closesAt(next, quote, triple) && (triple || !isLineBreak(next))) {
      if (next == BACKSLASH) {
        consumeChars(ESCAPE_LENGTH);
      } else if (next == OPEN_BRACE) {
        scanBrace();
      } else {
        consumeChars(1);
      }
      next = _input.LA(1);
    }
    if (closesAt(next, quote, triple)) {
      consumeChars(quoteLength(triple));
    }
  }

  /** Consumes an escaped brace, or a replacement field whose opening brace is the next character. */
  private void scanBrace() {
    consumeChars(1);
    if (_input.LA(1) == OPEN_BRACE) {
      consumeChars(1);
    } else {
      scanReplacementField();
    }
  }

  private void scanReplacementField() {
    int depth = 0;
    int next = _input.LA(1);
    while (next != IntStream.EOF && (depth > 0 || next != CLOSE_BRACE)) {
      if (next == SINGLE_QUOTE || next == DOUBLE_QUOTE) {
        scanNestedString(next);
      } else {
        depth = nestingDepth(next, depth);
        consumeChars(1);
      }
      next = _input.LA(1);
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
    final boolean triple = _input.LA(2) == quote && _input.LA(3) == quote;
    consumeChars(quoteLength(triple));
    int next = _input.LA(1);
    while (next != IntStream.EOF && !closesAt(next, quote, triple) && (triple || !isLineBreak(next))) {
      consumeChars(next == BACKSLASH ? ESCAPE_LENGTH : 1);
      next = _input.LA(1);
    }
    if (closesAt(next, quote, triple)) {
      consumeChars(quoteLength(triple));
    }
  }

  /** Whether the character at the cursor, {@code next}, closes a string opened with {@code quote}. */
  private boolean closesAt(final int next, final int quote, final boolean triple) {
    return next == quote && (!triple || _input.LA(2) == quote && _input.LA(3) == quote);
  }

  private static boolean isLineBreak(final int next) {
    return next == '\n' || next == '\r';
  }

  private static int quoteLength(final boolean triple) {
    return triple ? TRIPLE_QUOTES : 1;
  }

  private void consumeChars(final int count) {
    for (int index = 0; index < count && _input.LA(1) != IntStream.EOF; index++) {
      getInterpreter().consume(_input);
    }
  }

  @Override
  public void reset() {
    tokens = new LinkedList<>();
    indents = new ArrayDeque<>();
    opened = 0;
    lastToken = Optional.empty();
    super.reset();
  }
}
