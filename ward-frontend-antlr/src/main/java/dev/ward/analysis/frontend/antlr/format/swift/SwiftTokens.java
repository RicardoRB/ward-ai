package dev.ward.analysis.frontend.antlr.format.swift;

import java.util.Set;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

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
          "if",
          "guard",
          "while",
          "for",
          "switch",
          "return",
          "in",
          "case",
          "let",
          "var",
          "else",
          "throw",
          "try",
          "is",
          "as",
          "do",
          "catch",
          "where",
          "defer",
          "repeat"
      );

  /** Postfix operators that can follow an operand, such as optional chaining and unwrapping. */
  static final Set<String> POSTFIX = Set.of("?", "!");

  private static final Set<String> BINARY =
      Set.of(
          "=",
          "==",
          "!=",
          "===",
          "!==",
          "<=",
          ">=",
          "&&",
          "||",
          "??",
          "+",
          "-",
          "*",
          "/",
          "%",
          "&",
          "|",
          "^",
          "+=",
          "-=",
          "*=",
          "/=",
          "%=",
          "->"
      );
  /** The keywords that a {@code ?} or {@code !} written right after them belongs to. */
  private static final Set<String> MARKED_KEYWORDS = Set.of("as", "try");

  private static final Set<String> PREFIX_CAPABLE = Set.of("+", "-", "&", "~", "!");
  private static final Set<String> CLOSERS = Set.of(")", "]");
  /** A name or a number; a name can start with {@code $} or stand in backticks. */
  private static final Pattern WORD =
      Pattern.compile("[$]?[\\p{L}\\p{N}_]+|[0-9][\\p{L}\\p{N}_.]*|`[^`]+`");
  private static final Pattern OPERATOR_TEXT = Pattern.compile("[=<>!&|^~+*/%?.\\-]+");
  private static final String STRING_START = "\"";
  private static final String RANGE_START = ".";

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
        || isPostfixMark(token);
  }

  /**
   * Tells whether the token is a {@code ?} or {@code !} written right after its operand. The
   * {@code ?} of a conditional expression has a space before it and ends no operand, so a minus
   * after it is a prefix: {@code flag ? -1 : 1}.
   */
  private static boolean isPostfixMark(final Token token) {
    final int start = token.getStartIndex();
    if (!POSTFIX.contains(token.getText()) || start == 0) {
      return false;
    }
    final String before = token.getInputStream().getText(Interval.of(start - 1, start - 1));
    return !before.isBlank();
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
        && !isAdjacentOperator(before, operator)
        && !isKeywordMark(before, operator);
  }

  /**
   * Tells whether the token is the {@code ?} or {@code !} of {@code as?}, {@code as!}, {@code
   * try?} or {@code try!}: the mark is written right after the keyword and is a part of it, so no
   * space may come between them.
   *
   * @param before the token before the mark, or null
   * @param mark the token that may be the mark
   * @return true when the mark belongs to the keyword before it
   */
  static boolean isKeywordMark(final Token before, final Token mark) {
    return before != null
        && POSTFIX.contains(mark.getText())
        && MARKED_KEYWORDS.contains(before.getText())
        && before.getStopIndex() + 1 == mark.getStartIndex();
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

  /**
   * Tells whether the token ends a range operator ({@code ...} or {@code ..<}). Swift reads a
   * range operator with a space on one side only as a prefix or a postfix operator, so the operand
   * after it stays attached.
   *
   * @param before the token before the earlier one, or null
   * @param previous the earlier token
   * @return true when the earlier token is the last character of a range operator
   */
  static boolean endsRange(final Token before, final Token previous) {
    final String text = previous.getText();
    return isAdjacentOperator(before, previous)
        && RANGE_START.equals(before.getText())
        && (RANGE_START.equals(text) || "<".equals(text));
  }

  /**
   * Tells whether the operator that the earlier token ends has no operand on its left, as in the
   * compilation condition {@code compiler(>=6)}: Swift reads it as a prefix operator, which may
   * not be separated from its operand.
   *
   * @param before the token before the earlier one, or null
   * @param previous the earlier token
   * @return true when a parenthesis or a comma stands right before the operator
   */
  static boolean hasNoLeftOperand(final Token before, final Token previous) {
    final Token first = isAdjacentOperator(before, previous) ? before : previous;
    final int start = first.getStartIndex();
    if (start == 0) {
      return true;
    }
    final String ahead = first.getInputStream().getText(Interval.of(start - 1, start - 1));
    return "(".equals(ahead) || ",".equals(ahead);
  }

  private static boolean isAdjacentOperator(final Token before, final Token token) {
    return before != null
        && before.getStopIndex() + 1 == token.getStartIndex()
        && isOperatorText(before.getText());
  }
}
