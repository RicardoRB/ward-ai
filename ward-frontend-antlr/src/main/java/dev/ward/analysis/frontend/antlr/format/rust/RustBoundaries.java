package dev.ward.analysis.frontend.antlr.format.rust;

import dev.ward.analysis.frontend.antlr.format.rust.RustFrame.Kind;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * Tells where a statement ends and where its next line continues it, from the tokens before and
 * after a line break.
 */
final class RustBoundaries {
  private static final String WHERE = "where";
  private static final String ELSE = "else";
  private static final String OPEN_BRACE = "{";
  private static final Set<String> ASSIGNMENTS = Set.of("=", "=>", "+=", "-=", "*=", "/=", "%=");
  private static final Set<RustKind> CLOSERS =
      Set.of(RustKind.CLOSE, RustKind.RIGHT_BRACE, RustKind.ANGLE_CLOSE);

  private final List<Token> tokens;
  private final List<RustKind> kinds;
  private final RustBrackets brackets;

  /**
   * Prepares the analysis of one source.
   *
   * @param tokens the significant tokens
   * @param kinds their kinds
   * @param brackets their matched brackets
   */
  RustBoundaries(
      final List<Token> tokens,
      final List<RustKind> kinds,
      final RustBrackets brackets
  ) {
    this.tokens = List.copyOf(tokens);
    this.kinds = List.copyOf(kinds);
    this.brackets = brackets;
  }

  /**
   * Tells whether a token closes a bracket, a generic angle included.
   *
   * @param index the token's position
   * @return true for a closer
   */
  boolean isCloser(final int index) {
    return CLOSERS.contains(kinds.get(index));
  }

  /**
   * The closing bracket of an opener.
   *
   * @param index the position of the opener
   * @return the closing position, or {@link RustFrame#NO_CLOSE}
   */
  int closeFor(final int index) {
    return brackets.closeFor(index);
  }

  /**
   * The nearest code token at or before a position, comments skipped.
   *
   * @param from the position to start at
   * @return the position, or -1
   */
  int lastCode(final int from) {
    for (int index = from; index >= 0; index--) {
      if (kinds.get(index) != RustKind.COMMENT) {
        return index;
      }
    }
    return -1;
  }

  /**
   * The nearest code token at or after a position, comments skipped.
   *
   * @param from the position to start at
   * @return the position, or -1
   */
  int nextCode(final int from) {
    for (int index = from; index < tokens.size(); index++) {
      if (kinds.get(index) != RustKind.COMMENT) {
        return index;
      }
    }
    return -1;
  }

  /**
   * Tells whether a line break between two code tokens starts a new statement or item.
   *
   * @param before the code token before the break, or -1
   * @param after the code token after the break, or -1
   * @return true when the next line does not continue the statement
   */
  boolean isBoundary(final int before, final int after) {
    if (before < 0 || after < 0) {
      return true;
    }
    final String next = tokens.get(after).getText();
    if (OPEN_BRACE.equals(next)
        || WHERE.equals(next)
        || ELSE.equals(next)
        || kinds.get(after) == RustKind.PATTERN_BAR) {
      return true;
    }
    return switch (kinds.get(before)) {
      case SEMI, LEFT_BRACE, OPEN, COMMA, ANGLE_OPEN -> true;
      case RIGHT_BRACE -> kinds.get(after) != RustKind.DOT && kinds.get(after) != RustKind.BINARY;
      default -> isAttributeEnd(before);
    };
  }

  /**
   * A chain after a call or block whose line starts with its closing bracket keeps the indent.
   *
   * @param before the code token before the break, or -1
   * @param after the code token after the break, or -1
   * @return true when the next line is a chain item that stays at the indent it has
   */
  boolean joinsMultiline(final int before, final int after) {
    if (before < 0 || after < 0 || kinds.get(after) != RustKind.DOT || !isCloser(before)) {
      return false;
    }
    final int opener = brackets.openFor(before);
    return opener >= 0
        && tokens.get(opener).getLine() != tokens.get(before).getLine()
        && isCloser(firstOnLine(before));
  }

  /**
   * Tells whether a token is an assignment-like operator, after which a statement part continues.
   *
   * @param index the token's position
   * @return true for {@code =}, {@code =>} and the compound assignments
   */
  boolean isAssignment(final int index) {
    return kinds.get(index) == RustKind.BINARY && ASSIGNMENTS.contains(tokens.get(index).getText());
  }

  /**
   * What a bracket does to the indent of its lines.
   *
   * @param index the position of the opener
   * @return plain when it opens and closes on one line or hugs a later opener, otherwise indent
   */
  Kind frameKind(final int index) {
    if (!brackets.spansLines(index) || brackets.opensSpanningLater(index)) {
      return Kind.PLAIN;
    }
    if (kinds.get(index) == RustKind.LEFT_BRACE) {
      return Kind.INDENT;
    }
    final boolean sameLine =
        index + 1 < tokens.size() && tokens.get(index + 1).getLine() == tokens.get(index).getLine();
    return sameLine ? Kind.HUGGING : Kind.INDENT;
  }

  private boolean isAttributeEnd(final int index) {
    final int opener = brackets.openFor(index);
    if (kinds.get(index) != RustKind.CLOSE || opener < 1 || !"]".equals(
        tokens.get(index).getText()
    )) {
      return false;
    }
    final boolean outer = kinds.get(opener - 1) == RustKind.POUND;
    return outer
        || opener >= 2
        && kinds.get(opener - 2) == RustKind.POUND
        && kinds.get(opener - 1) == RustKind.PREFIX;
  }

  private int firstOnLine(final int index) {
    int first = index;
    while (first > 0 && tokens.get(first - 1).getLine() == tokens.get(index).getLine()) {
      first--;
    }
    return first;
  }
}
