package dev.ward.analysis.frontend.antlr.swift;

import java.util.BitSet;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.Interval;

/**
 * Swift-specific predicates for the Swift grammar: operator classification by Unicode range,
 * whitespace around operators, and statement separation. Adapted from the grammars-v4 swift3
 * grammar's support class (BSD-3-Clause, Terence Parr).
 */
public final class SwiftSupport {

  /** Number of code points in the Basic Multilingual Plane, the size of the operator bitsets. */
  private static final int BMP_SIZE = 0x10000;

  /** Number of 16-bit characters in the whitespace tables. */
  private static final int ASCII_SIZE = 255;

  /** Operator-head characters that are ASCII symbols. */
  private static final String OPERATOR_HEAD_CHARS = "/=-+!*%<>&|^~?";

  /**
   * Operator-head code point ranges as inclusive (first, last) pairs, from the Swift grammar's
   * {@code operator-head} rule.
   */
  private static final int[] OPERATOR_HEAD_RANGES = {
    0x00A1, 0x00A7,
    0x00A9, 0x00A9,
    0x00AB, 0x00AB,
    0x00AC, 0x00AC,
    0x00AE, 0x00AE,
    0x00B0, 0x00B1,
    0x00B6, 0x00B6,
    0x00BB, 0x00BB,
    0x00BF, 0x00BF,
    0x00D7, 0x00D7,
    0x00F7, 0x00F7,
    0x2016, 0x2017,
    0x2020, 0x2027,
    0x2030, 0x203E,
    0x2041, 0x2053,
    0x2055, 0x205E,
    0x2190, 0x23FF,
    0x2500, 0x2775,
    0x2794, 0x2BFF,
    0x2E00, 0x2E7F,
    0x3001, 0x3003,
    0x3008, 0x3030,
  };

  /**
   * Operator-character code point ranges as inclusive (first, last) pairs. The operator-head
   * characters are included separately. The Unicode ranges above U+FFFF are not included, because
   * Java characters are 16 bits wide.
   */
  private static final int[] OPERATOR_CHARACTER_RANGES = {
    0x0300, 0x036F,
    0x1DC0, 0x1DFF,
    0x20D0, 0x20FF,
    0xFE00, 0xFE0F,
    0xFE20, 0xFE2F,
  };

  private static final BitSet OPERATOR_HEAD = new BitSet(BMP_SIZE);
  private static final BitSet OPERATOR_CHARACTER;
  private static final BitSet LEFT_WS = new BitSet(ASCII_SIZE);
  private static final BitSet RIGHT_WS = new BitSet(ASCII_SIZE);

  static {
    for (final char c : OPERATOR_HEAD_CHARS.toCharArray()) {
      OPERATOR_HEAD.set(c);
    }
    setRanges(OPERATOR_HEAD, OPERATOR_HEAD_RANGES);

    // operator-character includes every operator-head
    OPERATOR_CHARACTER = (BitSet) OPERATOR_HEAD.clone();
    setRanges(OPERATOR_CHARACTER, OPERATOR_CHARACTER_RANGES);

    LEFT_WS.set(SwiftParser.WS);
    LEFT_WS.set(SwiftParser.LPAREN);
    LEFT_WS.set(SwiftParser.LBRACK);
    LEFT_WS.set(SwiftParser.LCURLY);
    LEFT_WS.set(SwiftParser.COMMA);
    LEFT_WS.set(SwiftParser.COLON);
    LEFT_WS.set(SwiftParser.SEMI);

    RIGHT_WS.set(SwiftParser.WS);
    RIGHT_WS.set(SwiftParser.RPAREN);
    RIGHT_WS.set(SwiftParser.RBRACK);
    RIGHT_WS.set(SwiftParser.RCURLY);
    RIGHT_WS.set(SwiftParser.COMMA);
    RIGHT_WS.set(SwiftParser.COLON);
    RIGHT_WS.set(SwiftParser.SEMI);
    RIGHT_WS.set(SwiftParser.Line_comment);
    RIGHT_WS.set(SwiftParser.Block_comment);
  }

  /** Prevents instantiation: this class only holds static helpers. */
  private SwiftSupport() {}

  /** Sets every code point from each inclusive (first, last) pair. */
  private static void setRanges(final BitSet set, final int... ranges) {
    for (int i = 0; i < ranges.length; i += 2) {
      set.set(ranges[i], ranges[i + 1] + 1);
    }
  }

  /** Tells whether the token is a single character that can start an operator. */
  public static boolean isOperatorHead(final Token token) {
    return isCharacterFromSet(token, OPERATOR_HEAD);
  }

  /** Tells whether the token is a single character that can continue an operator. */
  public static boolean isOperatorCharacter(final Token token) {
    return isCharacterFromSet(token, OPERATOR_CHARACTER);
  }

  private static boolean isCharacterFromSet(final Token token, final BitSet bitSet) {
    if (token.getType() == Token.EOF) {
      return false;
    }
    final String text = token.getText();
    final int codepoint = text.codePointAt(0);
    // a token of more than one character is not a single operator character
    return Character.charCount(codepoint) == text.length() && bitSet.get(codepoint);
  }

  /** Tells whether an operator starts at the current position. */
  public static boolean isOpNext(final TokenStream tokens) {
    fillUp(tokens);
    final int stop = getLastOpTokenIndex(tokens);
    return stop != -1;
  }

  /** Finds the stop token index of the next operator; returns -1 if there is no operator. */
  public static int getLastOpTokenIndex(final TokenStream tokens) {
    fillUp(tokens);
    int currentTokenIndex = tokens.index(); // current on-channel lookahead token index
    Token currentToken = tokens.get(currentTokenIndex);

    // operator → dot-operator-head­ dot-operator-characters
    if (currentToken.getType() == SwiftParser.DOT
        && tokens.get(currentTokenIndex + 1).getType() == SwiftParser.DOT) {
      // dot-operator
      currentTokenIndex += 2; // point at token after ".."
      currentToken = tokens.get(currentTokenIndex);

      // dot-operator-character → .­ | operator-character­
      while (currentToken.getType() == SwiftParser.DOT || isOperatorCharacter(currentToken)) {
        currentTokenIndex++;
        currentToken = tokens.get(currentTokenIndex);
      }
      return currentTokenIndex - 1;
    }

    // operator → operator-head­ operator-characters­?
    if (isOperatorHead(currentToken)) {
      while (isOperatorCharacter(currentToken)) {
        currentTokenIndex++;
        currentToken = tokens.get(currentTokenIndex);
      }
      return currentTokenIndex - 1;
    }
    return -1;
  }

  /**
   * "If an operator has whitespace around both sides or around neither side, it is treated as a
   * binary operator. As an example, the + operator in a+b and a + b is treated as a binary
   * operator."
   */
  public static boolean isBinaryOp(final TokenStream tokens) {
    fillUp(tokens);
    final int stop = getLastOpTokenIndex(tokens);
    if (stop == -1) {
      return false;
    }

    final int start = tokens.index();
    final Token prevToken = previousToken(tokens, start); // includes hidden-channel tokens
    final Token nextToken = tokens.get(stop + 1);
    final boolean prevIsWS = isLeftOperatorWS(prevToken);
    final boolean nextIsWS = isRightOperatorWS(nextToken);
    return prevIsWS && nextIsWS || !prevIsWS && !nextIsWS;
  }

  /**
   * "If an operator has whitespace on the left side only, it is treated as a prefix unary operator.
   * As an example, the ++ operator in a ++b is treated as a prefix unary operator."
   */
  public static boolean isPrefixOp(final TokenStream tokens) {
    fillUp(tokens);
    final int stop = getLastOpTokenIndex(tokens);
    if (stop == -1) {
      return false;
    }

    final int start = tokens.index();
    final Token prevToken = previousToken(tokens, start); // includes hidden-channel tokens
    final Token nextToken = tokens.get(stop + 1);
    final boolean prevIsWS = isLeftOperatorWS(prevToken);
    final boolean nextIsWS = isRightOperatorWS(nextToken);
    return prevIsWS && !nextIsWS;
  }

  /**
   * "If an operator has whitespace on the right side only, it is treated as a postfix unary
   * operator. As an example, the ++ operator in a++ b is treated as a postfix unary operator."
   *
   * <p>"If an operator has no whitespace on the left but is followed immediately by a dot (.), it
   * is treated as a postfix unary operator. As an example, the ++ operator in a++.b is treated as a
   * postfix unary operator (a++ .b rather than a ++ .b)."
   */
  public static boolean isPostfixOp(final TokenStream tokens) {
    fillUp(tokens);
    final int stop = getLastOpTokenIndex(tokens);
    if (stop == -1) {
      return false;
    }

    final int start = tokens.index();
    final Token prevToken = previousToken(tokens, start); // includes hidden-channel tokens
    final Token nextToken = tokens.get(stop + 1);
    final boolean prevIsWS = isLeftOperatorWS(prevToken);
    final boolean nextIsWS = isRightOperatorWS(nextToken);
    return !prevIsWS && nextIsWS || !prevIsWS && nextToken.getType() == SwiftParser.DOT;
  }

  /** Tells whether the operator at the current position has the given text. */
  public static boolean isOperator(final TokenStream tokens, final String op) {
    fillUp(tokens);
    final int stop = getLastOpTokenIndex(tokens);
    if (stop == -1) {
      return false;
    }

    final int start = tokens.index();
    final String text = tokens.getText(Interval.of(start, stop));
    return text.equals(op);
  }

  /**
   * Tells whether a token counts as whitespace on the left of an operator. The start of the input
   * counts as whitespace, so an operator at the very beginning is a prefix operator.
   */
  public static boolean isLeftOperatorWS(final Token t) {
    return t == null || LEFT_WS.get(t.getType());
  }

  /** Returns the token before {@code start}, or {@code null} at the start of the input. */
  private static Token previousToken(final TokenStream tokens, final int start) {
    return start > 0 ? tokens.get(start - 1) : null;
  }

  /**
   * Tells whether a token counts as whitespace on the right of an operator; the end of input
   * counts.
   */
  public static boolean isRightOperatorWS(final Token t) {
    return t.getType() == Token.EOF || RIGHT_WS.get(t.getType());
  }

  /**
   * Tells whether a newline or a semicolon separates the previous statement from the current one.
   */
  public static boolean isSeparatedStatement(
      final TokenStream tokens, final int indexOfPreviousStatement) {
    fillUp(tokens);
    int indexFrom = indexOfPreviousStatement - 1;
    final int indexTo = tokens.index() - 1;

    // Stupid check for new line and semicolon, can be optimized
    if (indexFrom < 0) {
      return true;
    }
    while (indexFrom >= 0 && tokens.get(indexFrom).getChannel() == Token.HIDDEN_CHANNEL) {
      indexFrom--;
    }
    final String between = tokens.getText(Interval.of(indexFrom, indexTo));
    return between.contains("\n") || between.contains(";");
  }

  /** Reads the stream to its end, so that the lookahead the predicates use is available. */
  public static void fillUp(final TokenStream tokens) {
    for (int jj = 1; ; ++jj) {
      final int t = tokens.LA(jj);
      if (t == -1) {
        break;
      }
    }
  }
}
