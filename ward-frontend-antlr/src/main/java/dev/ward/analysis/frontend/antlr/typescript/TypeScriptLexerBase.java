package dev.ward.analysis.frontend.antlr.typescript;

import dev.ward.analysis.frontend.antlr.JsxLookahead;
import dev.ward.analysis.frontend.antlr.WardLexerBase;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

  /** Token types after which a less-than sign is an operator, so no JSX element can start. */
  private static final Set<Integer> NO_JSX_AFTER =
      Stream.concat(
              NO_REGEX_AFTER.stream(),
              Stream.of(TypeScriptLexer.JsxSelfCloseEnd, TypeScriptLexer.JsxClosingTagEnd))
          .collect(Collectors.toUnmodifiableSet());

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

  /** The template literals the lexer is inside, and the braces inside their substitutions. */
  protected final TemplateNesting templates = new TemplateNesting();

  /**
   * Counts every open brace of the default mode, so a JSX container knows its own closing brace.
   */
  private int braceNesting;

  /** The brace nesting of each open JSX expression container, innermost on top. */
  private final Deque<Integer> jsxContainers = new ArrayDeque<>();

  /** Whether JSX syntax is recognized; set per unit from the file extension. */
  private boolean jsxEnabled;

  /**
   * Creates the lexer over the given input.
   *
   * @param input the character stream to lex
   */
  public TypeScriptLexerBase(final CharStream input) {
    super(input);
  }

  /**
   * Tells whether JSX syntax is recognized, which is the case for {@code .tsx} files only.
   *
   * @return {@code true} if a {@code <} can start a JSX element
   */
  public boolean isJsx() {
    return jsxEnabled;
  }

  /**
   * Turns JSX syntax on or off for the next unit. Set it before parsing; the lexer keeps it across
   * resets.
   *
   * @param value {@code true} for {@code .tsx} units
   */
  public void setJsx(final boolean value) {
    jsxEnabled = value;
  }

  /**
   * Tells whether a {@code <} directly before the input starts a JSX element here. Only a name or a
   * fragment closer can follow, and a generic arrow function's type parameters like {@code <T,>} or
   * {@code <T extends U>} are not JSX.
   *
   * @return {@code true} if a JSX element starts at the {@code <} just read
   */
  protected boolean isJsxStart() {
    return jsxEnabled && isJsxExpressionPosition() && JsxLookahead.startsElement(_input);
  }

  private boolean isJsxExpressionPosition() {
    return lastToken.isEmpty() || !NO_JSX_AFTER.contains(lastToken.get().getType());
  }

  /**
   * Starts a JSX expression container: the brace counts like any other, and its closing brace
   * returns the lexer to the JSX mode that opened it.
   */
  protected void processJsxOpenBrace() {
    processOpenBrace();
    jsxContainers.push(braceNesting);
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
    templates.braceOpened();
    braceNesting++;
    useStrictCurrent = !scopeStrictModes.isEmpty() && scopeStrictModes.peek() || useStrictDefault;
    scopeStrictModes.push(useStrictCurrent);
  }

  protected void processCloseBrace() {
    templates.braceClosed();
    useStrictCurrent = scopeStrictModes.isEmpty() ? useStrictDefault : scopeStrictModes.pop();
    if (!jsxContainers.isEmpty() && jsxContainers.peek() == braceNesting) {
      jsxContainers.pop();
      popMode();
    }
    braceNesting--;
  }

  /** Clears the state of the previous unit; the JSX setting is kept, as the caller sets it. */
  @Override
  public void reset() {
    scopeStrictModes.clear();
    lastToken = Optional.empty();
    useStrictDefault = false;
    useStrictCurrent = false;
    templates.reset();
    braceNesting = 0;
    jsxContainers.clear();
    super.reset();
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
