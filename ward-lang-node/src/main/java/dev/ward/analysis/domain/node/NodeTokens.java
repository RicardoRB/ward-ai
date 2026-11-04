package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads the tokens of one file by position. Positions past either end read as empty text. */
final class NodeTokens {
  private static final String TEXT = "text";
  private static final String DOT = ".";
  private static final String OPEN_PAREN = "(";
  private static final String IMPORT = "import";
  private static final String EXPORT = "export";
  private static final String REQUIRE_WORD = "require";
  private static final String QUOTE = "'";

  private final List<SyntaxNode> leaves;

  /** Creates the reader over the tokens, in source order. */
  NodeTokens(final List<SyntaxNode> leaves) {
    this.leaves = List.copyOf(leaves);
  }

  int size() {
    return leaves.size();
  }

  SyntaxNode leaf(final int i) {
    return leaves.get(i);
  }

  String text(final int i) {
    return i < 0 || i >= leaves.size() ? "" : leaves.get(i).fields().getOrDefault(TEXT, "");
  }

  String kind(final int i) {
    return i < 0 || i >= leaves.size() ? "" : leaves.get(i).kind();
  }

  /** Returns whether token {@code i} is the word, and is not a property after a dot. */
  boolean isKeyword(final int i, final String word) {
    return word.equals(text(i)) && !DOT.equals(text(i - 1));
  }

  /**
   * Returns whether token {@code i} is the punctuation mark, which the grammar gives a quoted kind.
   */
  boolean isPunctuation(final int i, final String symbol) {
    return symbol.equals(text(i)) && kind(i).startsWith(QUOTE);
  }

  /** Returns whether token {@code i} starts a call of {@code require} or {@code import}. */
  boolean isCall(final int i) {
    return (isKeyword(i, REQUIRE_WORD) || isKeyword(i, IMPORT)) && OPEN_PAREN.equals(text(i + 1));
  }

  /** Returns whether token {@code i} starts an import or export statement, not a call. */
  boolean isStatementKeyword(final int i) {
    return isKeyword(i, EXPORT)
        || (isKeyword(i, IMPORT) && !OPEN_PAREN.equals(text(i + 1)) && !DOT.equals(text(i + 1)));
  }

  /** Removes the quotes of a string literal. */
  static String unquote(final String literal) {
    return literal.length() >= 2 ? literal.substring(1, literal.length() - 1) : literal;
  }
}
