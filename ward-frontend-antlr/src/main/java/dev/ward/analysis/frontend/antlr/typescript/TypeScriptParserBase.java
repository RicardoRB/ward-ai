package dev.ward.analysis.frontend.antlr.typescript;

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

  protected boolean notLineTerminator() {
    return !lineTerminatorAhead();
  }

  protected boolean notOpenBraceAndNotFunctionAndNotInterface() {
    final int nextTokenType = _input.LT(1).getType();
    return nextTokenType != TypeScriptParser.OpenBrace
        && nextTokenType != TypeScriptParser.Function_
        && nextTokenType != TypeScriptParser.Interface;
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
