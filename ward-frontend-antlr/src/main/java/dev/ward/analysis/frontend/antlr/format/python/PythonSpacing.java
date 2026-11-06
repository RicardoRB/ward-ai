package dev.ward.analysis.frontend.antlr.format.python;

import dev.ward.analysis.frontend.antlr.python.Python3Lexer;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether two neighbouring Python tokens on one line are separated by a space. Only spaces
 * change: the decision never adds or removes a token.
 */
final class PythonSpacing {

  private static final Set<String> OPENERS = Set.of("(", "[", "{");
  private static final Set<String> CLOSERS = Set.of(")", "]", "}");
  private static final Set<String> BEFORE_NO_SPACE = Set.of(",", ";", ":");
  private static final Set<String> AFTER_SPACE = Set.of(",", ";");
  private static final Set<String> LITERALS = Set.of("True", "False", "None");
  private static final Set<String> KEYWORDS =
      Set.of(
          "and",
          "as",
          "assert",
          "async",
          "await",
          "break",
          "class",
          "continue",
          "def",
          "del",
          "elif",
          "else",
          "except",
          "finally",
          "for",
          "from",
          "global",
          "if",
          "import",
          "in",
          "is",
          "lambda",
          "nonlocal",
          "not",
          "or",
          "pass",
          "raise",
          "return",
          "try",
          "while",
          "with",
          "yield");
  private static final Set<String> OPERATORS =
      Set.of(
          "=", "+=", "-=", "*=", "/=", "//=", "%=", "**=", ">>=", "<<=", "&=", "|=", "^=", "@=",
          ":=", "->", "==", "!=", "<", ">", "<=", ">=", "+", "-", "*", "/", "//", "%", "**", "<<",
          ">>", "&", "|", "^", "@", "~");
  private static final Set<String> UNARY_CAPABLE = Set.of("+", "-", "*", "**", "~");
  private static final String DECORATOR = "@";
  private static final String COLON = ":";
  private static final String DOT = ".";
  private static final String EQUALS = "=";
  private static final String IMPORT = "import";
  private static final String FROM = "from";

  private PythonSpacing() {}

  /**
   * Tells whether a token closes a bracket.
   *
   * @param text the token text
   * @return true for {@code )}, {@code ]} and {@code }}
   */
  static boolean isCloser(final String text) {
    return CLOSERS.contains(text);
  }

  /**
   * Tells whether a token opens a bracket.
   *
   * @param text the token text
   * @return true for {@code (}, {@code [} and {@code {}
   */
  static boolean isOpener(final String text) {
    return OPENERS.contains(text);
  }

  /**
   * Tells whether an opening square bracket starts a subscript, which follows a value.
   *
   * @param opener the bracket text
   * @param previous the token before the bracket, or null
   * @return true for a subscript
   */
  static boolean isSubscript(final String opener, final PythonEntry previous) {
    return "[".equals(opener) && isValueEnd(previous);
  }

  /**
   * Tells whether a token ends a value, so that a following bracket is a call or a subscript.
   *
   * @param entry the token, or null at the start of a line
   * @return true for a name, a literal, a number, a string or a closing bracket
   */
  static boolean isValueEnd(final PythonEntry entry) {
    if (entry == null) {
      return false;
    }
    final String text = entry.text();
    if (isCloser(text) || LITERALS.contains(text)) {
      return true;
    }
    if (entry.kind() != PythonEntry.Kind.TOKEN) {
      return false;
    }
    final int type = entry.tokenType();
    return type == Python3Lexer.NUMBER
        || type == Python3Lexer.STRING
        || type == Python3Lexer.NAME && !KEYWORDS.contains(text);
  }

  /**
   * Tells whether a token is an operator that can take a space on each side.
   *
   * @param text the token text
   * @return true for assignment, comparison, arithmetic and bitwise operators
   */
  static boolean isOperator(final String text) {
    return OPERATORS.contains(text);
  }

  /**
   * Tells whether an operator is a prefix operator, such as a sign or a star argument.
   *
   * @param before the token before the operator, or null
   * @param operator the operator text
   * @return true when the operator has no operand before it
   */
  static boolean isUnary(final PythonEntry before, final String operator) {
    if (DECORATOR.equals(operator)) {
      return before == null;
    }
    return UNARY_CAPABLE.contains(operator) && !isValueEnd(before);
  }

  /**
   * Decides whether a space goes between two tokens of one line.
   *
   * @param beforePrevious the token before {@code previous}, or null
   * @param previous the token before {@code next}
   * @param next the token to place
   * @param frame the innermost open bracket, or the line itself
   * @return true for a space
   */
  static boolean space(
      final PythonEntry beforePrevious,
      final PythonEntry previous,
      final PythonEntry next,
      final PythonFrame frame) {
    final String prev = previous.text();
    final String nextText = next.text();
    if (frame.isParenthesis() && (EQUALS.equals(prev) || EQUALS.equals(nextText))) {
      return frame.annotated();
    }
    final Optional<Boolean> punctuation = punctuationSpace(prev, nextText, frame);
    return punctuation.orElseGet(() -> operatorSpace(beforePrevious, previous, next));
  }

  /** Decides the spaces next to brackets and separators; empty when operators decide. */
  private static Optional<Boolean> punctuationSpace(
      final String prev, final String next, final PythonFrame frame) {
    if (isCloser(next) || OPENERS.contains(prev) || BEFORE_NO_SPACE.contains(next)) {
      return Optional.of(false);
    }
    if (AFTER_SPACE.contains(prev)) {
      return Optional.of(true);
    }
    if (COLON.equals(prev)) {
      return Optional.of(!frame.subscript());
    }
    return Optional.empty();
  }

  private static boolean operatorSpace(
      final PythonEntry beforePrevious, final PythonEntry previous, final PythonEntry next) {
    final String nextText = next.text();
    if (isOperator(previous.text()) && isUnary(beforePrevious, previous.text())) {
      return false;
    }
    return isOperator(nextText) && !isUnary(previous, nextText) || spaceAround(previous, nextText);
  }

  private static boolean spaceAround(final PythonEntry previous, final String next) {
    final String prev = previous.text();
    if (isOperator(prev)) {
      return true;
    }
    if (DOT.equals(prev)) {
      return IMPORT.equals(next);
    }
    if (DOT.equals(next)) {
      return FROM.equals(prev);
    }
    return !OPENERS.contains(next) || !isValueEnd(previous);
  }
}
