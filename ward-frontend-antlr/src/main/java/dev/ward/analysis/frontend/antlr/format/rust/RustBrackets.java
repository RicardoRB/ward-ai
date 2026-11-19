package dev.ward.analysis.frontend.antlr.format.rust;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/** Matches the brackets of a token list and tells which of them span lines. */
final class RustBrackets {
  private static final Set<RustKind> OPENERS =
      Set.of(RustKind.OPEN, RustKind.LEFT_BRACE, RustKind.ANGLE_OPEN, RustKind.QUALIFIED_OPEN);
  private static final Set<RustKind> CLOSERS =
      Set.of(RustKind.CLOSE, RustKind.RIGHT_BRACE, RustKind.ANGLE_CLOSE);

  private final List<Token> tokens;
  private final int[] closes;
  private final int[] opens;

  /**
   * Matches the brackets of the tokens: round, square and curly brackets and generic angles.
   *
   * @param tokens the significant tokens
   * @param kinds their kinds
   */
  RustBrackets(final List<Token> tokens, final List<RustKind> kinds) {
    this.tokens = List.copyOf(tokens);
    this.closes = new int[tokens.size()];
    this.opens = new int[tokens.size()];
    match(kinds);
  }

  /**
   * The opening bracket of a closer.
   *
   * @param index the position of the closing bracket
   * @return the opening position, or {@link RustFrame#NO_CLOSE}
   */
  int openFor(final int index) {
    return opens[index];
  }

  /**
   * The closing bracket of an opener.
   *
   * @param index the position of the opener
   * @return the closing position, or {@link RustFrame#NO_CLOSE}
   */
  int closeFor(final int index) {
    return closes[index];
  }

  /**
   * Tells whether a bracket closes on another line than it opens.
   *
   * @param index the position of the opener
   * @return true when the closing bracket is on a later line
   */
  boolean spansLines(final int index) {
    return tokens.get(index).getLine() != tokens.get(closes[index]).getLine();
  }

  /**
   * Tells whether another bracket that opens on the same line also spans lines, so that only the
   * last of them indents, as in a call that ends its line with a closure body.
   *
   * @param index the position of the opener
   * @return true when a later opener on the line spans lines
   */
  boolean opensSpanningLater(final int index) {
    final int line = tokens.get(index).getLine();
    for (int later = index + 1; later < tokens.size(); later++) {
      if (tokens.get(later).getLine() != line) {
        return false;
      }
      if (closes[later] != RustFrame.NO_CLOSE && spansLines(later)) {
        return true;
      }
    }
    return false;
  }

  private void match(final List<RustKind> kinds) {
    Arrays.fill(closes, RustFrame.NO_CLOSE);
    Arrays.fill(opens, RustFrame.NO_CLOSE);
    final Deque<Integer> open = new ArrayDeque<>();
    for (int index = 0; index < kinds.size(); index++) {
      if (OPENERS.contains(kinds.get(index))) {
        open.push(index);
      } else if (CLOSERS.contains(kinds.get(index)) && !open.isEmpty()) {
        final int opener = open.pop();
        closes[opener] = index;
        opens[index] = opener;
      }
    }
  }
}
