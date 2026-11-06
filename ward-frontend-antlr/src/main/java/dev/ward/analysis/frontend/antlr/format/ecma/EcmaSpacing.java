package dev.ward.analysis.frontend.antlr.format.ecma;

import dev.ward.analysis.domain.format.Document;
import java.util.List;
import java.util.Set;

/**
 * The spacing between two tokens on one line. A space is only ever added or removed where the two
 * tokens cannot merge, so the text of each token stays exactly as written.
 */
final class EcmaSpacing {

  private static final String EMPTY = "";
  private static final String SPACE = " ";
  private static final String COMMA = ",";
  private static final String SEMICOLON = ";";
  private static final String OPEN_BRACE = "{";
  private static final String CLOSE_BRACE = "}";
  private static final String COLON = ":";
  private static final Set<String> NO_SPACE_AFTER = Set.of("(", "[", ".", "?.");
  private static final Set<String> NO_SPACE_BEFORE = Set.of(")", "]", ",", ";", ".", "?.");
  private static final Set<String> SPACED_OPERATORS =
      Set.of("=", "=>", "==", "===", "!=", "!==", "+=", "-=", "*=", "/=", "&&", "||", "??");
  private static final Set<String> ARITHMETIC = Set.of("+", "-", "*", "/", "%");
  private static final Set<String> KEYWORDS_BEFORE_PARENTHESIS =
      Set.of(
          "if", "for", "while", "switch", "return", "typeof", "void", "delete", "await", "yield",
          "throw", "case");

  private final List<EcmaToken> tokens;

  /**
   * Binds the spacing rules to the tokens of one source.
   *
   * @param tokens the layout tokens in order
   */
  EcmaSpacing(final List<EcmaToken> tokens) {
    this.tokens = List.copyOf(tokens);
  }

  /**
   * Returns the space between two adjacent tokens on the same line, or none.
   *
   * @param prev the index of the first token
   * @param next the index of the second token
   * @return a single space or nothing
   */
  Document between(final int prev, final int next) {
    final String before = text(prev);
    final String after = text(next);
    if (NO_SPACE_AFTER.contains(before) || NO_SPACE_BEFORE.contains(after)) {
      return Document.text(EMPTY);
    }
    if (isSpaced(before, after) || isBinaryAt(prev, next) || isKeywordCall(prev, next)) {
      return Document.text(SPACE);
    }
    return Document.text(tokens.get(next).spaceBefore() ? SPACE : EMPTY);
  }

  private static boolean isSpaced(final String before, final String after) {
    return COMMA.equals(before)
        || SEMICOLON.equals(before)
        || COLON.equals(before)
        || OPEN_BRACE.equals(before)
        || OPEN_BRACE.equals(after)
        || CLOSE_BRACE.equals(before)
        || CLOSE_BRACE.equals(after)
        || SPACED_OPERATORS.contains(before)
        || SPACED_OPERATORS.contains(after);
  }

  /** An arithmetic sign is binary, with a space on each side, when an operand comes before it. */
  private boolean isBinaryAt(final int prev, final int next) {
    return (ARITHMETIC.contains(text(next)) && prev >= 0 && EcmaText.isOperand(text(prev)))
        || (prev >= 1 && ARITHMETIC.contains(text(prev)) && EcmaText.isOperand(text(prev - 1)));
  }

  /** A keyword such as {@code if} or {@code return} takes a space before its parenthesis. */
  private boolean isKeywordCall(final int prev, final int next) {
    final String after = text(next);
    final boolean member = prev >= 1 && (".".equals(text(prev - 1)) || "?.".equals(text(prev - 1)));
    return KEYWORDS_BEFORE_PARENTHESIS.contains(text(prev))
        && ("(".equals(after) || "[".equals(after))
        && !member;
  }

  private String text(final int index) {
    return tokens.get(index).text();
  }
}
