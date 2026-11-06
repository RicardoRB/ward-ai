package dev.ward.analysis.frontend.antlr.format.swift;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/** Matches the brackets of a token list and answers the layout questions about them. */
final class SwiftBrackets {

  private static final String OPEN_PAREN = "(";
  private static final String OPEN_BRACKET = "[";
  private static final String OPEN_BRACE = "{";
  private static final String COMMA = ",";
  private static final Set<String> OPENERS = Set.of(OPEN_PAREN, OPEN_BRACKET, OPEN_BRACE);
  private static final Set<String> CLOSERS = Set.of(")", "]", "}");

  private final List<Token> tokens;
  private final int[] closes;

  /**
   * Matches the brackets of the tokens.
   *
   * @param tokens the significant tokens, in source order
   */
  SwiftBrackets(final List<Token> tokens) {
    this.tokens = tokens;
    this.closes = matchBrackets(tokens);
  }

  /**
   * The index of the bracket that closes the one at the index.
   *
   * @param index the index of an opening bracket
   * @return the closing index, or {@link SwiftFrame#NO_CLOSE} when there is none
   */
  int closeFor(final int index) {
    return closes[index];
  }

  /**
   * Tells whether the bracket pair at the index spans more than one source line.
   *
   * @param index the index of an opening bracket that has a closing bracket
   * @return true when the two brackets are on different lines
   */
  boolean spansLines(final int index) {
    return tokens.get(index).getLine() != tokens.get(closes[index]).getLine();
  }

  /**
   * Tells whether the bracket pair at the index holds a comma outside any nested bracket.
   *
   * @param index the index of an opening bracket that has a closing bracket
   * @return true when such a comma exists
   */
  boolean hasTopLevelComma(final int index) {
    int depth = 0;
    for (int inner = index + 1; inner < closes[index]; inner++) {
      final String text = tokens.get(inner).getText();
      if (OPENERS.contains(text)) {
        depth++;
      } else if (CLOSERS.contains(text)) {
        depth--;
      } else if (depth == 0 && COMMA.equals(text)) {
        return true;
      }
    }
    return false;
  }

  private static int[] matchBrackets(final List<Token> tokens) {
    final int[] matches = new int[tokens.size()];
    Arrays.fill(matches, SwiftFrame.NO_CLOSE);
    final Deque<Integer> open = new ArrayDeque<>();
    for (int index = 0; index < tokens.size(); index++) {
      final String text = tokens.get(index).getText();
      if (OPENERS.contains(text)) {
        open.push(index);
      } else if (CLOSERS.contains(text) && !open.isEmpty()) {
        matches[open.pop()] = index;
      }
    }
    return matches;
  }
}
