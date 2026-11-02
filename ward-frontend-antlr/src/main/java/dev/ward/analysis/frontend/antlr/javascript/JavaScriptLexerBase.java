package dev.ward.analysis.frontend.antlr.javascript;

import dev.ward.analysis.frontend.antlr.WardLexerBase;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.Set;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;

/**
 * Lexer state and helper methods used by the JavaScript grammar's semantic predicates and actions.
 *
 * <p>Method names start with a lower-case letter, as Ward's style requires; the grammar calls them
 * by these names. Adapted from the grammars-v4 JavaScript base class (MIT).
 */
public abstract class JavaScriptLexerBase extends WardLexerBase {

  /** Token types after which a slash starts a division, so no regex literal can follow. */
  private static final Set<Integer> NO_REGEX_AFTER =
      Set.of(
          JavaScriptLexer.Identifier,
          JavaScriptLexer.NullLiteral,
          JavaScriptLexer.BooleanLiteral,
          JavaScriptLexer.This,
          JavaScriptLexer.CloseBracket,
          JavaScriptLexer.CloseParen,
          JavaScriptLexer.OctalIntegerLiteral,
          JavaScriptLexer.DecimalLiteral,
          JavaScriptLexer.HexIntegerLiteral,
          JavaScriptLexer.StringLiteral,
          JavaScriptLexer.PlusPlus,
          JavaScriptLexer.MinusMinus);

  /** Stores values of nested modes. By default mode is strict or defined externally. */
  private final Deque<Boolean> scopeStrictModes = new ArrayDeque<>();

  /** The most recently produced token on the default channel. */
  private Optional<Token> lastToken = Optional.empty();

  /** Default value of strict mode. Can be defined externally by {@link #setUseStrictDefault}. */
  private boolean useStrictDefault;

  /**
   * Current value of strict mode. Can be changed while parsing, for example by a {@code 'use
   * strict'} directive.
   */
  private boolean useStrictCurrent;

  /** Preserves depth due to braces including template literals. */
  private int currentDepth;

  /** Preserves the starting depth of template literals to correctly handle braces inside them. */
  private Deque<Integer> templateDepthStack = new ArrayDeque<>();

  /**
   * Creates the lexer over the given input.
   *
   * @param input the character stream to lex
   */
  public JavaScriptLexerBase(final CharStream input) {
    super(input);
  }

  /**
   * Tells whether no token has been produced yet.
   *
   * @return {@code true} at the start of the input
   */
  public boolean isStartOfFile() {
    return lastToken.isEmpty();
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

  /**
   * Tells whether the lexer is directly inside a template literal's substitution.
   *
   * @return {@code true} if a closing brace ends a template substitution
   */
  public boolean isInTemplateString() {
    return !templateDepthStack.isEmpty() && templateDepthStack.peek() == currentDepth;
  }

  /**
   * Returns the next token from the character stream and records it as the last token when it is on
   * the default channel. That token decides whether a regex literal is possible.
   *
   * @return the next token from the character stream
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

  /** Handles an opening brace: enters a scope that keeps the current strict mode. */
  protected void processOpenBrace() {
    currentDepth++;
    useStrictCurrent = !scopeStrictModes.isEmpty() && scopeStrictModes.peek() || useStrictDefault;
    scopeStrictModes.push(useStrictCurrent);
  }

  /** Handles a closing brace: leaves the innermost scope and restores its strict mode. */
  protected void processCloseBrace() {
    useStrictCurrent = scopeStrictModes.isEmpty() ? useStrictDefault : scopeStrictModes.pop();
    currentDepth--;
  }

  /** Handles the opening brace of a template substitution. */
  protected void processTemplateOpenBrace() {
    currentDepth++;
    this.templateDepthStack.push(currentDepth);
  }

  /** Handles the closing brace of a template substitution. */
  protected void processTemplateCloseBrace() {
    this.templateDepthStack.pop();
    currentDepth--;
  }

  /** Switches to strict mode when a string literal is the {@code 'use strict'} directive. */
  protected void processStringLiteral() {
    final boolean afterOpenBraceOrFileStart =
        lastToken.isEmpty() || lastToken.get().getType() == JavaScriptLexer.OpenBrace;
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

  /**
   * Tells whether the lexer can match a regex literal. At the start of the input a regex is
   * possible; after one of the tokens in {@code NO_REGEX_AFTER} it is not.
   *
   * @return {@code true} if a regex literal can start here
   */
  protected boolean isRegexPossible() {
    return lastToken.isEmpty() || !NO_REGEX_AFTER.contains(lastToken.get().getType());
  }

  @Override
  public void reset() {
    this.scopeStrictModes.clear();
    this.lastToken = Optional.empty();
    this.useStrictDefault = false;
    this.useStrictCurrent = false;
    this.currentDepth = 0;
    this.templateDepthStack = new ArrayDeque<>();
    super.reset();
  }
}
