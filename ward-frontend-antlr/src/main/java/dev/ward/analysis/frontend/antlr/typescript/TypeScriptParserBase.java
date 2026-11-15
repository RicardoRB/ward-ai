package dev.ward.analysis.frontend.antlr.typescript;

import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * All parser methods that used in grammar (p, prev, notLineTerminator, etc.) should start with
 * lower case char similar to parser rules.
 */
public abstract class TypeScriptParserBase extends Parser {
  /**
   * Creates the parser over the given token stream.
   *
   * @param input the tokens to parse
   */
  public TypeScriptParserBase(final TokenStream input) {
    super(input);
  }

  /** Short form for prev(final String str) */
  protected boolean p(final String str) {
    return prev(str);
  }

  /** Whether the previous token value equals to @param str */
  protected boolean prev(final String str) {
    return _input.LT(-1).getText().equals(str);
  }

  /** Short form for next(final String str) */
  protected boolean n(final String str) {
    return next(str);
  }

  /** Whether the next token value equals to @param str */
  protected boolean next(final String str) {
    return _input.LT(1).getText().equals(str);
  }

  /**
   * Tells whether the lexer recognizes JSX here, so JSX alternatives may match. The lexer makes the
   * same decision from the file extension, so the tokens and this predicate agree.
   *
   * @return {@code true} for a {@code .tsx} or {@code .jsx} unit
   */
  protected boolean isJsx() {
    return _input instanceof BufferedTokenStream tokens
        && tokens.getTokenSource() instanceof TypeScriptLexerBase lexer
        && lexer.isJsx();
  }

  /**
   * Whether the {@code <} at the current token is a comparison rather than the start of the type
   * arguments of a call such as {@code f<T[]>(x)}; see {@link TypeArgumentsLookahead}. The grammar
   * cannot tell the two apart by itself: both parse, and the comparison would win.
   *
   * @return {@code false} iff the current token is a {@code <} that opens type arguments of a call
   */
  protected boolean notTypeArgumentsCallAhead() {
    return !TypeArgumentsLookahead.opensCall(_input);
  }

  protected boolean notLineTerminator() {
    return !lineTerminatorAhead();
  }

  protected boolean notOpenBraceAndNotFunctionAndNotInterface() {
    final int nextTokenType = _input.LT(1).getType();
    return nextTokenType != TypeScriptParser.OpenBrace
        && nextTokenType != TypeScriptParser.Function_
        && nextTokenType != TypeScriptParser.Interface;
  }

  /**
   * Whether the tokens after an {@code export} start an export clause, such as {@code { a }} or
   * {@code type { a }}, or a default export such as {@code default function () {}}. Such a clause
   * is an export statement, so the {@code export} is not a prefix of the statement that follows.
   */
  protected boolean notExportClauseAhead() {
    int offset = 2;
    if (_input.LT(offset).getType() == TypeScriptParser.TypeAlias) {
      offset++;
    }
    final int next = _input.LT(offset).getType();
    return next != TypeScriptParser.OpenBrace && next != TypeScriptParser.Default;
  }

  protected boolean closeBrace() {
    return _input.LT(1).getType() == TypeScriptParser.CloseBrace;
  }

  /**
   * Returns {@code true} iff on the current index of the parser's token stream a token exists on
   * the {@code HIDDEN} channel which either is a line terminator, or is a multi line comment that
   * contains a line terminator.
   *
   * @return {@code true} iff on the current index of the parser's token stream a token exists on
   *     the {@code HIDDEN} channel which either is a line terminator, or is a multi line comment
   *     that contains a line terminator.
   */
  protected boolean lineTerminatorAhead() {

    // Get the token ahead of the current index.
    int possibleIndexEosToken = this.getCurrentToken().getTokenIndex() - 1;
    Token ahead = _input.get(possibleIndexEosToken);

    if (ahead.getChannel() != Lexer.HIDDEN) {
      // We're only interested in tokens on the HIDDEN channel.
      return false;
    }

    if (ahead.getType() == TypeScriptParser.LineTerminator) {
      // There is definitely a line terminator ahead.
      return true;
    }

    if (ahead.getType() == TypeScriptParser.WhiteSpaces) {
      // Get the token ahead of the current whitespaces.
      possibleIndexEosToken = this.getCurrentToken().getTokenIndex() - 2;
      ahead = _input.get(possibleIndexEosToken);
    }

    // Get the token's text and type.
    final String text = ahead.getText();
    final int type = ahead.getType();

    // Check if the token is, or contains a line terminator.
    return (type == TypeScriptParser.MultiLineComment
            && (text.contains("\r") || text.contains("\n")))
        || (type == TypeScriptParser.LineTerminator);
  }
}
