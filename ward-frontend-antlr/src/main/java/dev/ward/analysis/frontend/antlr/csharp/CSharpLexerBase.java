package dev.ward.analysis.frontend.antlr.csharp;

import dev.ward.analysis.frontend.antlr.WardLexerBase;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;

/**
 * Base class of the generated C# lexer: the lookahead and mode helpers its interpolated-string
 * rules call, and the interception of preprocessor directives. Derived from the {@code
 * CSharpLexerBase} of the grammars-v4 {@code csharp/v8-spec} grammar (MIT).
 */
public abstract class CSharpLexerBase extends WardLexerBase {
  private static final int INTERPOLATION_OPEN = '{';
  private static final int INTERPOLATION_CLOSE = '}';
  private static final char WRAP_OPEN = '〔';
  private static final char WRAP_CLOSE = '〕';

  private final CSharpPreprocessor preprocessor = new CSharpPreprocessor(this);

  /**
   * Creates the lexer over the given input.
   *
   * @param input the character stream to lex
   */
  protected CSharpLexerBase(final CharStream input) {
    super(input);
  }

  /** Returns the mode on top of the mode stack, or the default mode when it is empty. */
  public int peekMode() {
    return _modeStack.isEmpty() ? DEFAULT_MODE : _modeStack.peek();
  }

  @Override
  public int popMode() {
    return _modeStack.isEmpty() ? DEFAULT_MODE : super.popMode();
  }

  /** Returns whether the next character is a closing brace. */
  public boolean lookAheadIsRBrace1() {
    return getInputStream().LA(1) == INTERPOLATION_CLOSE;
  }

  /** Returns whether the second next character is not an opening brace. */
  public boolean lookAheadIsNotLBrace2() {
    return getInputStream().LA(2) != INTERPOLATION_OPEN;
  }

  /** Returns whether the lexer is inside the continuation of a regular interpolated string. */
  public boolean peekModeIsIrsCont() {
    return peekMode() == CSharpLexer.IRS_CONT;
  }

  /** Returns whether the lexer is inside the continuation of a verbatim interpolated string. */
  public boolean peekModeIsIvsCont() {
    return peekMode() == CSharpLexer.IVS_CONT;
  }

  /** Wraps the text of the current token in the delimiters that tell interpolated parts apart. */
  public void wrapToken() {
    final String close = String.valueOf(WRAP_CLOSE);
    setText(WRAP_OPEN + getText().replace(close, close + close) + close);
  }

  @Override
  public Token nextToken() {
    return preprocessor.next();
  }

  /** Returns the next token the generated lexer matches, before any directive handling. */
  Token rawNextToken() {
    return super.nextToken();
  }

  @Override
  public void reset() {
    super.reset();
    preprocessor.reset();
  }
}
