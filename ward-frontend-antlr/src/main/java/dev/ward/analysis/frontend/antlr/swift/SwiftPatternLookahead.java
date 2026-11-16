package dev.ward.analysis.frontend.antlr.swift;

import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * Predicates of the Swift grammar that read ahead over a pattern or a bracket: whether a pattern is
 * an expression, and whether a subscript has labelled arguments. Like {@link SwiftLookahead} they
 * read only the tokens.
 */
final class SwiftPatternLookahead {
  private static final String ASSIGN = "=";

  /** The tokens that end a pattern made of one name. */
  private static final Set<String> AFTER_NAME = Set.of(":", ",", ")", "in", "{", "where", "as");

  /** The tokens that end a pattern made of one enumeration case. */
  private static final Set<String> AFTER_CASE = Set.of(":", ",", ")", "{", "where");

  private SwiftPatternLookahead() {}

  /**
   * Whether the pattern at the next token can be an expression. One name, or one enumeration case
   * without a payload, is a binding or a case pattern.
   */
  static boolean isExpressionPattern(final TokenStream tokens) {
    final Token first = tokens.LT(1);
    if (first.getType() == SwiftParser.Identifier) {
      return !endsPattern(tokens, 2, AFTER_NAME);
    }
    final boolean enumCase =
        first.getType() == SwiftParser.DOT && tokens.LT(2).getType() == SwiftParser.Identifier;
    return !(enumCase && endsPattern(tokens, 2 + 1, AFTER_CASE));
  }

  /**
   * Whether the bracket at the next token opens a subscript whose first argument has a label, as
   * in {@code counts[key, default: 0]} or {@code table[row: 2]}.
   */
  static boolean isLabeledSubscript(final TokenStream tokens) {
    if (SwiftLookahead.isLineBreakBefore(tokens)) {
      return false;
    }
    int depth = 0;
    for (int ahead = 1; tokens.LA(ahead) != Token.EOF; ahead++) {
      final String text = tokens.LT(ahead).getText();
      depth -= depthChange(text);
      if (depth == 0) {
        return false;
      }
      if (depth == 1 && isLabelStart(tokens, ahead)) {
        return true;
      }
    }
    return false;
  }

  /** Whether a label and its colon follow the bracket or the comma {@code ahead} positions on. */
  private static boolean isLabelStart(final TokenStream tokens, final int ahead) {
    final String text = tokens.LT(ahead).getText();
    final boolean separator = "[".equals(text) || ",".equals(text);
    return separator
        && ":".equals(tokens.LT(ahead + 2).getText())
        && Character.isLetter(tokens.LT(ahead + 1).getText().charAt(0));
  }

  /** Whether the token {@code ahead} positions on ends a pattern: a listed token or one {@code =}. */
  private static boolean endsPattern(
      final TokenStream tokens,
      final int ahead,
      final Set<String> enders
  ) {
    final String text = tokens.LT(ahead).getText();
    if (ASSIGN.equals(text)) {
      return !ASSIGN.equals(tokens.LT(ahead + 1).getText());
    }
    return enders.contains(text);
  }

  private static boolean isOpening(final String text) {
    return "(".equals(text) || "[".equals(text) || "{".equals(text);
  }

  private static int depthChange(final String text) {
    if (")".equals(text) || "]".equals(text) || "}".equals(text)) {
      return 1;
    }
    return isOpening(text) ? -1 : 0;
  }
}
