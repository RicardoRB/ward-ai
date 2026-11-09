package dev.ward.analysis.frontend.antlr;

import org.antlr.v4.runtime.IntStream;

/**
 * Looks past a {@code <} to tell a JSX element from a less-than sign or a TypeScript type parameter
 * list. Shared by the TypeScript and JavaScript lexers, which both call it from their predicates.
 */
public final class JsxLookahead {

  private static final int FIRST = 1;
  private static final int GREATER_THAN = '>';
  private static final int COMMA = ',';
  private static final String EXTENDS = "extends";

  private JsxLookahead() {}

  /**
   * Tells whether the input after a {@code <} starts a JSX element: a tag name, or {@code >} for a
   * fragment. A generic arrow function's type parameters such as {@code <T,>} or {@code <T extends
   * U>} are not JSX.
   *
   * @param input the characters, with the {@code <} already consumed
   * @return {@code true} if a JSX element starts at that {@code <}
   */
  public static boolean startsElement(final IntStream input) {
    final int next = input.LA(FIRST);
    return next == GREATER_THAN || isNameStart(next) && !isTypeParameterList(input);
  }

  private static boolean isTypeParameterList(final IntStream input) {
    int offset = FIRST;
    while (isNamePart(input.LA(offset))) {
      offset++;
    }
    while (isSpace(input.LA(offset))) {
      offset++;
    }
    return input.LA(offset) == COMMA || startsWordAt(input, offset, EXTENDS);
  }

  private static boolean startsWordAt(final IntStream input, final int offset, final String word) {
    for (int i = 0; i < word.length(); i++) {
      if (input.LA(offset + i) != word.charAt(i)) {
        return false;
      }
    }
    return !isNamePart(input.LA(offset + word.length()));
  }

  private static boolean isNameStart(final int c) {
    return Character.isLetter(c) || c == '$' || c == '_';
  }

  private static boolean isNamePart(final int c) {
    return isNameStart(c) || Character.isDigit(c) || c == '-';
  }

  private static boolean isSpace(final int c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }
}
