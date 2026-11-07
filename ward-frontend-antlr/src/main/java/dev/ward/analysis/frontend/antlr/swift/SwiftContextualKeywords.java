package dev.ward.analysis.frontend.antlr.swift;

import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/** Contextual keywords of the Swift grammar: words that are keywords only in one position. */
final class SwiftContextualKeywords {

  /** Words that are keywords only where a type starts, so they stay identifiers elsewhere. */
  private static final Set<String> CONTEXTUAL_TYPE_KEYWORDS =
      Set.of("any", "some", "borrowing", "consuming", "sending", "each");

  /** Words that are prefix operators only before an operand, so they stay identifiers elsewhere. */
  private static final Set<String> CONTEXTUAL_OPERATOR_WORDS = Set.of("consume", "each");

  private SwiftContextualKeywords() {}

  /** Whether the next token is a contextual keyword that can start a type. */
  static boolean isContextualTypeKeyword(final TokenStream tokens) {
    return CONTEXTUAL_TYPE_KEYWORDS.contains(tokens.LT(1).getText());
  }

  /**
   * Whether the next token is {@code consume} or {@code each} applied to an operand on the same
   * statement.
   */
  static boolean isContextualOperator(final TokenStream tokens) {
    final String text = tokens.LT(1).getText();
    final boolean operatorWord = CONTEXTUAL_OPERATOR_WORDS.contains(text);
    final Token next = tokens.LT(2);
    return operatorWord
        && (next.getType() == SwiftLexer.Identifier || "self".equals(next.getText()));
  }
}
