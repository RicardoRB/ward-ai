package dev.ward.analysis.frontend.antlr.format.java;

import java.util.List;
import java.util.Set;

/** Token-text predicates the Java spacing rules share: words, literals, types and casts. */
final class JavaTokens {

  static final Set<String> KEYWORD_PAREN =
      Set.of("if", "for", "while", "switch", "catch", "synchronized", "try", "return", "throw");
  static final Set<String> PRIMITIVES =
      Set.of("int", "long", "short", "byte", "char", "boolean", "double", "float", "void");
  static final Set<String> RESERVED =
      Set.of(
          "abstract",
          "assert",
          "break",
          "case",
          "catch",
          "class",
          "const",
          "continue",
          "default",
          "do",
          "else",
          "enum",
          "extends",
          "final",
          "finally",
          "for",
          "goto",
          "if",
          "implements",
          "import",
          "instanceof",
          "interface",
          "native",
          "new",
          "package",
          "private",
          "protected",
          "public",
          "return",
          "static",
          "strictfp",
          "super",
          "switch",
          "synchronized",
          "this",
          "throw",
          "throws",
          "transient",
          "try",
          "volatile",
          "while",
          "yield",
          "true",
          "false",
          "null");
  private static final Set<String> OPERAND_WORDS = Set.of("this", "super", "true", "false", "null");
  private static final Set<String> TYPE_SYMBOLS = Set.of(".", "<", ">", ",", "[", "]", "?", "&");
  private static final Set<String> TYPE_WORDS = Set.of("extends", "super");
  private static final String OPEN_PAREN = "(";
  private static final String CLOSE_PAREN = ")";
  private static final String NOT = "!";
  private static final String TILDE = "~";
  private static final String PLUS = "+";
  private static final String MINUS = "-";
  private static final String NEW = "new";

  private JavaTokens() {}

  static boolean isWord(final String text) {
    final char first = text.charAt(0);
    return Character.isLetter(first) || first == '_' || first == '$';
  }

  static boolean isLiteral(final String text) {
    final char first = text.charAt(0);
    return Character.isDigit(first) || first == '"' || first == '\'';
  }

  /** Tells whether a token is a name, including {@code this}, {@code super} and the literals. */
  static boolean isIdentifier(final JavaAtom atom) {
    final String text = atom.text();
    return isWord(text) && (!RESERVED.contains(text) || OPERAND_WORDS.contains(text));
  }

  /** Tells whether a token can begin an operand, so a cast before it is a cast. */
  static boolean startsOperand(final JavaAtom atom) {
    final String text = atom.text();
    return isLiteral(text)
        || isIdentifier(atom)
        || NEW.equals(text)
        || OPEN_PAREN.equals(text)
        || NOT.equals(text)
        || TILDE.equals(text);
  }

  static boolean isTypeToken(final JavaAtom atom) {
    final String text = atom.text();
    return isWord(text)
            && (!RESERVED.contains(text) || PRIMITIVES.contains(text) || TYPE_WORDS.contains(text))
        || TYPE_SYMBOLS.contains(text);
  }

  static boolean isSign(final JavaAtom atom) {
    return PLUS.equals(atom.text()) || MINUS.equals(atom.text());
  }

  /** Tells whether the tokens strictly between two parentheses can only be a type. */
  static boolean castContentIsType(final List<JavaAtom> codes, final int open, final int close) {
    for (int i = open + 1; i < close; i++) {
      if (!isTypeToken(codes.get(i))) {
        return false;
      }
    }
    return true;
  }

  /** Returns the index of the parenthesis that closes the one at {@code open}, or -1. */
  static int matchingClose(final List<JavaAtom> codes, final int open) {
    int depth = 0;
    for (int i = open; i < codes.size(); i++) {
      final String text = codes.get(i).text();
      if (OPEN_PAREN.equals(text)) {
        depth++;
      } else if (CLOSE_PAREN.equals(text)) {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  static boolean followsKeywordParen(final List<JavaAtom> codes, final int index) {
    return index >= 0 && KEYWORD_PAREN.contains(codes.get(index).text());
  }
}
