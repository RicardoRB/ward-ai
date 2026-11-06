package dev.ward.analysis.frontend.antlr.format.swift;

import java.util.Set;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.Token;

/**
 * Classifies Swift tokens by their text for spacing. A token that ends an operand is an identifier
 * that is not a keyword, a literal, a closing bracket or a postfix {@code ?} or {@code !}. An
 * operator that can also be a prefix (such as {@code -}) is binary only after an operand. The
 * grammar can split one operator into adjacent tokens (for example {@code &&} or {@code ->}), so an
 * adjacent pair is read as one operator.
 */
final class SwiftTokens {

  /** Keywords that introduce an expression or a clause; an operand never ends with one. */
  static final Set<String> KEYWORDS =
      Set.of(
          "if", "guard", "while", "for", "switch", "return", "in", "case", "let", "var", "else",
          "throw", "try", "is", "as", "do", "catch", "where", "defer", "repeat");

  /** Postfix operators that can follow an operand, such as optional chaining and unwrapping. */
  static final Set<String> POSTFIX = Set.of("?", "!");

  private static final Set<String> BINARY =
      Set.of(
          "=", "==", "!=", "===", "!==", "<=", ">=", "&&", "||", "??", "+", "-", "*", "/", "%", "&",
          "|", "^", "+=", "-=", "*=", "/=", "%=", "->");
  private static final Set<String> PREFIX_CAPABLE = Set.of("+", "-", "&", "~", "!");
  private static final Set<String> CLOSERS = Set.of(")", "]");
  private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]+");
  private static final Pattern OPERATOR_TEXT = Pattern.compile("[=<>!&|^~+*/%?.\\-]+");
  private static final String STRING_START = "\"";

  private SwiftTokens() {}

  /**
   * Tells whether the text is made of operator characters.
   *
   * @param text the token text
   * @return true when every character is an operator character
   */
  static boolean isOperatorText(final String text) {
    return OPERATOR_TEXT.matcher(text).matches();
  }

  /**
   * Tells whether two adjacent tokens (no source space between them) are one operator that must
   * stay joined. A postfix mark joins only when the pair is a known binary operator, so that {@code
   * value!+ 1} keeps its space.
   *
   * @param previous the earlier token
   * @param text the text of the later token
   * @return true when the pair must stay joined
   */
  static boolean isGluedOperator(final Token previous, final String text) {
    final String prev = previous.getText();
    if (!isOperatorText(prev) || !isOperatorText(text)) {
      return false;
    }
    return !POSTFIX.contains(prev) || BINARY.contains(prev + text);
  }

  /**
   * Tells whether the token ends an operand, so that a following bracket or dot continues it.
   *
   * @param token the token, or null at the start of the file
   * @return true for an identifier, a literal, a closing bracket or a postfix operator
   */
  static boolean isOperandEnd(final Token token) {
    if (token == null) {
      return false;
    }
    final String text = token.getText();
    return (WORD.matcher(text).matches() && !KEYWORDS.contains(text))
        || text.startsWith(STRING_START)
        || CLOSERS.contains(text)
        || POSTFIX.contains(text);
  }

  /**
   * Tells whether the token is a prefix operator: a prefix-capable operator not preceded by an
   * operand, and not the second half of a split operator.
   *
   * @param before the token before the operator, or null
   * @param operator the operator token
   * @return true when the operator is a prefix
   */
  static boolean isPrefix(final Token before, final Token operator) {
    return PREFIX_CAPABLE.contains(operator.getText())
        && !isOperandEnd(before)
        && !isAdjacentOperator(before, operator);
  }

  /**
   * Tells whether the text is a binary or assignment operator at this place.
   *
   * @param before the token before the operator, or null
   * @param operator the operator text
   * @return true for a binary operator
   */
  static boolean isBinaryAt(final Token before, final String operator) {
    return BINARY.contains(operator)
        && (!PREFIX_CAPABLE.contains(operator) || isOperandEnd(before));
  }

  /**
   * Tells whether the token ends a binary operator: either an operator that is binary here, or the
   * second half of a split one whose joined text is binary.
   *
   * @param before the token before the earlier one, or null
   * @param previous the earlier token
   * @return true when the earlier token ends a binary operator
   */
  static boolean endsBinary(final Token before, final Token previous) {
    return (isAdjacentOperator(before, previous)
            && BINARY.contains(before.getText() + previous.getText()))
        || isBinaryAt(before, previous.getText());
  }

  private static boolean isAdjacentOperator(final Token before, final Token token) {
    return before != null
        && before.getStopIndex() + 1 == token.getStartIndex()
        && isOperatorText(before.getText());
  }
}
