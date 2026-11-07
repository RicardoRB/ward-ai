package dev.ward.analysis.frontend.antlr.format.ecma;

import java.util.Set;

/** Classifies token text for the layout: brackets, words and operands. */
final class EcmaText {

  /** Words after which a brace opens an object literal rather than a block. */
  static final Set<String> OBJECT_KEYWORDS =
      Set.of(
          "return",
          "typeof",
          "delete",
          "case",
          "yield",
          "await",
          "in",
          "of",
          "new",
          "throw",
          "default",
          "import",
          "export",
          "instanceof",
          "type",
          "const",
          "let",
          "var",
          "function");

  private EcmaText() {}

  /**
   * Tells whether a comma after this token ends a value, so dropping the comma before a closer
   * keeps the list: a comma after another comma or an opener is an array hole.
   *
   * @param before the token before the comma
   * @return true when the token is a value, not a comma or an opener
   */
  static boolean endsValue(final String before) {
    return !",".equals(before) && !isOpener(before);
  }

  /**
   * Tells whether the text opens a bracket.
   *
   * @param text the token text
   * @return true for a parenthesis, a square bracket or a brace
   */
  static boolean isOpener(final String text) {
    return "(".equals(text) || "[".equals(text) || "{".equals(text);
  }

  /**
   * Tells whether the text closes a bracket.
   *
   * @param text the token text
   * @return true for a parenthesis, a square bracket or a brace
   */
  static boolean isCloser(final String text) {
    return ")".equals(text) || "]".equals(text) || "}".equals(text);
  }

  /**
   * Tells whether the text is an identifier or a keyword.
   *
   * @param text the token text
   * @return true when the text is a word
   */
  static boolean isWord(final String text) {
    if (text.isEmpty()) {
      return false;
    }
    final char first = text.charAt(0);
    final boolean start = Character.isLetter(first) || first == '_' || first == '$';
    return start
        && text.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '$');
  }

  /**
   * Tells whether the text ends an operand, so that a following sign is binary.
   *
   * @param text the token text
   * @return true for a value, a name that is not a keyword, or a closing parenthesis or bracket
   */
  static boolean isOperand(final String text) {
    if (text.isEmpty()) {
      return false;
    }
    final char first = text.charAt(0);
    if (Character.isDigit(first) || first == '"' || first == '\'' || first == '`') {
      return true;
    }
    if (isWord(text)) {
      return !OBJECT_KEYWORDS.contains(text);
    }
    return ")".equals(text) || "]".equals(text);
  }
}
