package dev.ward.analysis.frontend.antlr.format.csharp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * The braces of a C# parse: how many are open before each item, which of them enclose a line
 * break, and where the formatter must put a brace or a chained keyword on a new line.
 */
final class CSharpBraces {
  private static final String BRACE_OPEN = "{";
  private static final String BRACE_CLOSE = "}";
  private static final Set<String> CHAINED = Set.of("else", "catch", "finally");

  private final List<Token> items;
  private final int[] codePoints;
  private final CSharpTreeWalk walk;
  private final int[] open;
  private final boolean[] multiLine;

  /**
   * Scans the braces of a parse.
   *
   * @param parse the parse of the source
   */
  CSharpBraces(final CSharpParse parse) {
    this.items = parse.items();
    this.codePoints = parse.codePoints();
    this.walk = parse.walk();
    this.open = new int[items.size() + 1];
    this.multiLine = new boolean[items.size()];
    final List<Integer> stack = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      open[i + 1] = open[i];
      if (isBrace(i, BRACE_OPEN)) {
        stack.add(i);
        open[i + 1]++;
      } else if (isBrace(i, BRACE_CLOSE) && !stack.isEmpty()) {
        final int opening = stack.remove(stack.size() - 1);
        final boolean spans = items.get(opening).getLine() != items.get(i).getLine();
        multiLine[opening] = spans;
        multiLine[i] = spans;
        open[i + 1]--;
      }
    }
  }

  /**
   * Returns the number of braces open before an item.
   *
   * @param index the item's index
   * @return the count; the index just past the last item is allowed
   */
  int openBefore(final int index) {
    return open[index];
  }

  /**
   * Tells whether an item is a brace of the given kind, outside interpolated strings.
   *
   * @param index the item's index
   * @param brace {@code {} or {@code }}
   * @return true for such a brace
   */
  boolean isBrace(final int index, final String brace) {
    final Token token = items.get(index);
    return walk.stringOf(token.getTokenIndex()) < 0
        && !CSharpTokens.isComment(token)
        && brace.equals(CSharpTokens.text(codePoints, token));
  }

  /**
   * Tells whether an item must start a line: the opening brace of a block that spans lines, and an
   * {@code else}, {@code catch} or {@code finally} after the closing brace of such a block.
   *
   * @param index the item's index
   * @return true when a line break must precede the item
   */
  boolean mustBreak(final int index) {
    if (isBrace(index, BRACE_OPEN)) {
      final boolean arrayBrace =
          walk.role(items.get(index).getTokenIndex()) == CSharpRole.ARRAY_BRACE;
      return multiLine[index] && !arrayBrace;
    }
    return index > 0
        && CHAINED.contains(CSharpTokens.text(codePoints, items.get(index)))
        && isBrace(index - 1, BRACE_CLOSE)
        && multiLine[index - 1];
  }
}
