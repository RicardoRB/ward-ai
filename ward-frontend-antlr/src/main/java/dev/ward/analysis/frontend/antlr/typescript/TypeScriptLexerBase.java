package dev.ward.analysis.frontend.antlr.typescript;

import dev.ward.analysis.frontend.antlr.WardLexerBase;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.Set;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;

/**
 * All lexer methods that used in grammar (isStrictMode) should start with Upper Case Char similar
 * to Lexer rules.
 */
public abstract class TypeScriptLexerBase extends WardLexerBase {
  /** Token types after which a slash starts a division, so no regex literal can follow. */
  private static final Set<Integer> NO_REGEX_AFTER =
      Set.of(
          TypeScriptLexer.Identifier,
          TypeScriptLexer.NullLiteral,
          TypeScriptLexer.BooleanLiteral,
          TypeScriptLexer.This,
          TypeScriptLexer.CloseBracket,
          TypeScriptLexer.CloseParen,
          TypeScriptLexer.OctalIntegerLiteral,
          TypeScriptLexer.DecimalLiteral,
          TypeScriptLexer.HexIntegerLiteral,
          TypeScriptLexer.StringLiteral,
          TypeScriptLexer.PlusPlus,
          TypeScriptLexer.MinusMinus);

  /**
   * Stores values of nested modes. By default mode is strict or defined externally
   * (useStrictDefault)
   */
  private final Deque<Boolean> scopeStrictModes = new ArrayDeque<>();

  private Optional<Token> lastToken = Optional.empty();

  /** Default value of strict mode Can be defined externally by setUseStrictDefault */
  private boolean useStrictDefault;

  /**
   * Current value of strict mode Can be defined during parsing, see StringFunctions.js and
   * StringGlobal.js samples
   */
  private boolean useStrictCurrent;

  /**
   * Keeps track of the current depth of nested template string backticks. E.g. after the X in:
   *
   * <p>`${a ? `${X
   *
   * <p>templateDepth will be 2. This variable is needed to determine if a `}` is a plain
   * CloseBrace, or one that closes an expression inside a template string.
   */
  private int templateDepth;

  /**
   * Keeps track of the depth of open- and close-braces. Used for expressions like:
   *
   * <p>`${[1, 2, 3].map(x => { return x * 2;}).join("")}`
   *
   * <p>where the '}' from `return x * 2;}` should not become a `TemplateCloseBrace` token but
   * rather a `CloseBrace` token.
   */
  private int bracesDepth;

  /**
   * Creates the lexer over the given input.
   *
   * @param input the character stream to lex
   */
  public TypeScriptLexerBase(final CharStream input) {
    super(input);
  }

  /**
   * Returns the default value of strict mode.
   *
   * @return {@code true} if strict mode is the default
   */
  public boolean isStrictDefault() {
    return useStrictDefault;
  }

  /**
   * Sets the default value of strict mode and the current value with it.
   *
   * @param value the new default
   */
  public void setUseStrictDefault(final boolean value) {
    useStrictDefault = value;
    useStrictCurrent = value;
  }

  /**
   * Tells whether strict mode is on at the current position.
   *
   * @return {@code true} in strict mode
   */
  public boolean isStrictMode() {
    return useStrictCurrent;
  }

  /** Resets the brace count when a template literal starts. */
  public void startTemplateString() {
    this.bracesDepth = 0;
  }

  /**
   * Tells whether the lexer is directly inside a template literal's substitution.
   *
   * @return {@code true} if a closing brace ends a template substitution
   */
  public boolean isInTemplateString() {
    return this.templateDepth > 0 && this.bracesDepth == 0;
  }

  /**
   * Return the next token from the character stream and records this last token in case it resides
   * on the default channel. This recorded token is used to determine when the lexer could possibly
   * match a regex literal. Also changes scopeStrictModes stack if tokenize special string 'use
   * strict';
   *
   * @return the next token from the character stream.
   */
  @Override
  public Token nextToken() {
    final Token next = super.nextToken();

    if (next.getChannel() == Token.DEFAULT_CHANNEL) {
      // Keep track of the last token on the default channel.
      this.lastToken = Optional.of(next);
    }

    return next;
  }

  protected void processOpenBrace() {
    bracesDepth++;
    useStrictCurrent = !scopeStrictModes.isEmpty() && scopeStrictModes.peek() || useStrictDefault;
    scopeStrictModes.push(useStrictCurrent);
  }

  protected void processCloseBrace() {
    bracesDepth--;
    useStrictCurrent = scopeStrictModes.isEmpty() ? useStrictDefault : scopeStrictModes.pop();
  }

  protected void processStringLiteral() {
    final boolean afterOpenBraceOrFileStart =
        lastToken.isEmpty() || lastToken.get().getType() == TypeScriptLexer.OpenBrace;
    if (afterOpenBraceOrFileStart && isUseStrictDirective(getText())) {
      if (!scopeStrictModes.isEmpty()) {
        scopeStrictModes.pop();
      }
      useStrictCurrent = true;
      scopeStrictModes.push(useStrictCurrent);
    }
  }

  private static boolean isUseStrictDirective(final String text) {
    return "\"use strict\"".equals(text) || "'use strict'".equals(text);
  }

  /** Enters one more template literal. */
  protected void increaseTemplateDepth() {
    this.templateDepth++;
  }

  /** Leaves one template literal. */
  protected void decreaseTemplateDepth() {
    this.templateDepth--;
  }

  /**
   * Tells whether the lexer can match a regex literal. At the start of the input a regex is
   * possible; after one of the tokens in {@code NO_REGEX_AFTER} it is not.
   *
   * @return {@code true} if a regex literal can start here
   */
  protected boolean isRegexPossible() {
    return lastToken.isEmpty() || !NO_REGEX_AFTER.contains(lastToken.get().getType());
  }
}
