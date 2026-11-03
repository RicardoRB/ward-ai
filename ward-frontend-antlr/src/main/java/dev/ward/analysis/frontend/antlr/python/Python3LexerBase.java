package dev.ward.analysis.frontend.antlr.python;

import dev.ward.analysis.frontend.antlr.WardLexerBase;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;

/** Lexer state and helpers used by the Python grammar's actions and predicates. */
abstract class Python3LexerBase extends WardLexerBase {
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

  @Override
  public void reset() {
    tokens = new LinkedList<>();
    indents = new ArrayDeque<>();
    opened = 0;
    lastToken = Optional.empty();
    super.reset();
  }
}
