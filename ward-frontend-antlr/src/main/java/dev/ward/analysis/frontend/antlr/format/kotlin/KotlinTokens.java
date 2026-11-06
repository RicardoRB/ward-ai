package dev.ward.analysis.frontend.antlr.format.kotlin;

import dev.ward.analysis.frontend.antlr.kotlin.KotlinLexer;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/** Classifies Kotlin tokens and reads the source gaps between them for the formatter. */
final class KotlinTokens {

  /** The token type given to every comment, as the format checks expect. */
  static final String COMMENT = "comment";

  private static final String LINE_COMMENT = "//";
  private static final String SHEBANG = "#!";
  private static final String CRLF = "\r\n";
  private static final String LF = "\n";

  private static final Set<Integer> OPENERS =
      Set.of(
          KotlinLexer.LPAREN,
          KotlinLexer.LSQUARE,
          KotlinLexer.LCURL,
          KotlinLexer.LineStrExprStart,
          KotlinLexer.MultiLineStrExprStart);
  private static final Set<Integer> CLOSERS =
      Set.of(KotlinLexer.RPAREN, KotlinLexer.RSQUARE, KotlinLexer.RCURL);
  private static final Set<Integer> STRING_PARTS =
      Set.of(
          KotlinLexer.QUOTE_OPEN,
          KotlinLexer.QUOTE_CLOSE,
          KotlinLexer.TRIPLE_QUOTE_OPEN,
          KotlinLexer.TRIPLE_QUOTE_CLOSE,
          KotlinLexer.MultiLineStringQuote,
          KotlinLexer.LineStrRef,
          KotlinLexer.LineStrText,
          KotlinLexer.LineStrEscapedChar,
          KotlinLexer.LineStrExprStart,
          KotlinLexer.MultiLineStrRef,
          KotlinLexer.MultiLineStrText,
          KotlinLexer.MultiLineStrEscapedChar,
          KotlinLexer.MultiLineStrExprStart);
  private static final Set<Integer> DELIMITERS =
      Set.of(
          KotlinLexer.QUOTE_OPEN,
          KotlinLexer.QUOTE_CLOSE,
          KotlinLexer.TRIPLE_QUOTE_OPEN,
          KotlinLexer.TRIPLE_QUOTE_CLOSE);

  /** Whether the source space between two tokens becomes a space, none, or stays as written. */
  enum Spacing {
    SPACE,
    NONE,
    KEEP
  }

  private static final Set<Integer> OPERAND_ENDS =
      Set.of(
          KotlinLexer.Identifier,
          KotlinLexer.IntegerLiteral,
          KotlinLexer.RealLiteral,
          KotlinLexer.LongLiteral,
          KotlinLexer.HexLiteral,
          KotlinLexer.BinLiteral,
          KotlinLexer.CharacterLiteral,
          KotlinLexer.BooleanLiteral,
          KotlinLexer.NullLiteral,
          KotlinLexer.THIS,
          KotlinLexer.RPAREN,
          KotlinLexer.RSQUARE,
          KotlinLexer.RCURL,
          KotlinLexer.QUOTE_CLOSE,
          KotlinLexer.TRIPLE_QUOTE_CLOSE);
  private static final Set<Integer> NO_SPACE_BEFORE =
      Set.of(
          KotlinLexer.COMMA,
          KotlinLexer.RPAREN,
          KotlinLexer.RSQUARE,
          KotlinLexer.DOT,
          KotlinLexer.COLONCOLON,
          KotlinLexer.COLON,
          KotlinLexer.SEMICOLON);
  private static final Set<Integer> NO_SPACE_AFTER =
      Set.of(KotlinLexer.LPAREN, KotlinLexer.LSQUARE, KotlinLexer.DOT, KotlinLexer.COLONCOLON);
  private static final Set<Integer> SPACE_AFTER = Set.of(KotlinLexer.COMMA, KotlinLexer.COLON);
  private static final Set<Integer> UNARY_PREFIX =
      Set.of(
          KotlinLexer.ADD, KotlinLexer.SUB, KotlinLexer.EXCL, KotlinLexer.INCR, KotlinLexer.DECR);
  private static final Set<Integer> BINARY =
      Set.of(
          KotlinLexer.MULT,
          KotlinLexer.DIV,
          KotlinLexer.MOD,
          KotlinLexer.ADD,
          KotlinLexer.SUB,
          KotlinLexer.CONJ,
          KotlinLexer.DISJ,
          KotlinLexer.ASSIGNMENT,
          KotlinLexer.ADD_ASSIGNMENT,
          KotlinLexer.SUB_ASSIGNMENT,
          KotlinLexer.MULT_ASSIGNMENT,
          KotlinLexer.DIV_ASSIGNMENT,
          KotlinLexer.MOD_ASSIGNMENT,
          KotlinLexer.ELVIS,
          KotlinLexer.ARROW,
          KotlinLexer.EXCL_EQ,
          KotlinLexer.EQEQ,
          KotlinLexer.LE,
          KotlinLexer.GE);
  private static final Set<Integer> USE_SITE_TARGETS =
      Set.of(
          KotlinLexer.FIELD_SITE,
          KotlinLexer.FILE_SITE,
          KotlinLexer.PROPERTY_SITE,
          KotlinLexer.GET_SITE,
          KotlinLexer.SET_SITE,
          KotlinLexer.RECEIVER_SITE,
          KotlinLexer.PARAM_SITE,
          KotlinLexer.SETPARAM_SITE,
          KotlinLexer.DELEGATE_SITE);
  private static final Set<Integer> KEYWORDS_BEFORE_PAREN =
      Set.of(
          KotlinLexer.IF, KotlinLexer.FOR, KotlinLexer.WHILE, KotlinLexer.WHEN, KotlinLexer.CATCH);

  private KotlinTokens() {}

  /**
   * Decides the space between two tokens on one line from their types. A rule is a token pair, so a
   * rewrite never changes a token: only the space between tokens.
   *
   * @param before the token before {@code prev}, or null at the start
   * @param prev the token before the gap
   * @param next the token after the gap
   * @return the spacing for the gap
   */
  static Spacing spacing(final Token before, final Token prev, final Token next) {
    final int p = prev.getType();
    final int n = next.getType();
    if (p == KotlinLexer.COLON && before != null && USE_SITE_TARGETS.contains(before.getType())) {
      return Spacing.NONE;
    }
    if (NO_SPACE_BEFORE.contains(n)
        || (n == KotlinLexer.EXCL && (OPERAND_ENDS.contains(p) || p == KotlinLexer.EXCL))) {
      return Spacing.NONE;
    }
    if (NO_SPACE_AFTER.contains(p) || (n == KotlinLexer.LPAREN && OPERAND_ENDS.contains(p))) {
      return Spacing.NONE;
    }
    if (SPACE_AFTER.contains(p) || n == KotlinLexer.LCURL) {
      return Spacing.SPACE;
    }
    if (n == KotlinLexer.LPAREN && KEYWORDS_BEFORE_PAREN.contains(p)) {
      return Spacing.SPACE;
    }
    if (UNARY_PREFIX.contains(p) && !isOperandEnd(before)) {
      return Spacing.NONE;
    }
    return BINARY.contains(p) || BINARY.contains(n) ? Spacing.SPACE : Spacing.KEEP;
  }

  private static boolean isOperandEnd(final Token token) {
    return token != null && OPERAND_ENDS.contains(token.getType());
  }

  /**
   * Tells whether a token is a comment, which the lexer puts on the hidden channel.
   *
   * @param token the token
   * @return true for a comment
   */
  static boolean isComment(final Token token) {
    return token.getChannel() == Token.HIDDEN_CHANNEL;
  }

  /**
   * Tells whether a comment runs to the end of its line, so that a line must follow it.
   *
   * @param token the token
   * @return true for a line comment or a shebang line
   */
  static boolean isLineComment(final Token token) {
    final String text = token.getText();
    return isComment(token) && (text.startsWith(LINE_COMMENT) || text.startsWith(SHEBANG));
  }

  /**
   * Tells whether a token opens a bracket or a string template expression.
   *
   * @param token the token
   * @return true for an opener
   */
  static boolean isOpener(final Token token) {
    return OPENERS.contains(token.getType());
  }

  /**
   * Tells whether a token closes a bracket or a string template expression.
   *
   * @param token the token
   * @return true for a closer
   */
  static boolean isCloser(final Token token) {
    return CLOSERS.contains(token.getType());
  }

  /**
   * Tells whether a token is a comma.
   *
   * @param token the token
   * @return true for a comma
   */
  static boolean isComma(final Token token) {
    return token.getType() == KotlinLexer.COMMA;
  }

  /**
   * Tells whether the gap lies inside a string literal, where the source's own whitespace is part
   * of the value. A gap at a string's delimiter that faces code is outside the literal.
   *
   * @param prev the token before the gap
   * @param next the token after the gap
   * @return true when the gap must be copied as written
   */
  static boolean isVerbatim(final Token prev, final Token next) {
    final boolean prevString = STRING_PARTS.contains(prev.getType());
    final boolean nextString = STRING_PARTS.contains(next.getType());
    if (!prevString && !nextString) {
      return false;
    }
    if (DELIMITERS.contains(prev.getType()) && !nextString) {
      return false;
    }
    return !(DELIMITERS.contains(next.getType()) && !prevString);
  }

  /**
   * Returns the whitespace between two tokens. The tokens' indexes are code point indexes, so the
   * gap is read from the code points.
   *
   * @param codePoints the source code points
   * @param prev the token before the gap
   * @param next the token after the gap
   * @return the gap, empty when the tokens touch
   */
  static String gap(final int[] codePoints, final Token prev, final Token next) {
    final int start = prev.getStopIndex() + 1;
    final int length = next.getStartIndex() - start;
    return length <= 0 ? "" : new String(codePoints, start, length);
  }

  /**
   * Returns a token's text with carriage returns removed, so the printer alone writes line ends.
   *
   * @param token the token
   * @return the text
   */
  static String text(final Token token) {
    return token.getText().replace(CRLF, LF);
  }
}
