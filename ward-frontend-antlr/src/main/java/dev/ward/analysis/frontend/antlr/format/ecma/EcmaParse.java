package dev.ward.analysis.frontend.antlr.format.ecma;

import dev.ward.analysis.domain.format.FormatRule;
import dev.ward.analysis.domain.format.FormatSyntaxTree;
import dev.ward.analysis.domain.format.FormatToken;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * One parse of a TypeScript or JavaScript source: the tokens the formatter lays out, the tokens and
 * rule kinds the safety checks compare, and the first syntax error line.
 *
 * @param tokens the code tokens and comments in order, template literals as one token each
 * @param syntaxTokens every non-whitespace token in order, comments typed {@code comment}
 * @param rules the parse-tree rule kinds in pre-order
 * @param errorLine the line of the first syntax error, or empty when the source parses cleanly
 */
record EcmaParse(
    List<EcmaToken> tokens,
    List<FormatToken> syntaxTokens,
    List<FormatRule> rules,
    OptionalInt errorLine) {

  private static final String COMMA = ",";

  /** Copies the lists. */
  EcmaParse {
    tokens = List.copyOf(tokens);
    syntaxTokens = List.copyOf(syntaxTokens);
    rules = List.copyOf(rules);
  }

  /**
   * Returns what the safety checks need from this parse.
   *
   * @return the syntax tree view
   */
  FormatSyntaxTree toSyntaxTree() {
    return new FormatSyntaxTree(errorLine, withoutListEndingCommas(syntaxTokens), rules);
  }

  /**
   * Drops a comma that ends a list before its closing bracket. In ECMAScript such a comma does not
   * change the meaning, so the safety checks compare the lists without it. A comma after another
   * comma or an opener is a hole in an array, and stays.
   */
  private static List<FormatToken> withoutListEndingCommas(final List<FormatToken> tokens) {
    final List<FormatToken> kept = new ArrayList<>();
    for (int i = 0; i < tokens.size(); i++) {
      if (!isListEndingComma(tokens, i)) {
        kept.add(tokens.get(i));
      }
    }
    return kept;
  }

  private static boolean isListEndingComma(final List<FormatToken> tokens, final int index) {
    if (!COMMA.equals(tokens.get(index).text()) || index == 0 || index + 1 >= tokens.size()) {
      return false;
    }
    final String before = tokens.get(index - 1).text();
    return EcmaText.isCloser(tokens.get(index + 1).text())
        && !COMMA.equals(before)
        && !EcmaText.isOpener(before);
  }
}
