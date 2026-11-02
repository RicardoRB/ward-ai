package dev.ward.analysis.frontend.antlr.python;

/** Measures the indentation of a line of Python source. */
final class PythonIndentation {

  /** Tab stops for indentation: a tab advances to the next multiple of this width. */
  private static final int TAB_WIDTH = 8;

  private static final char TAB = '\t';

  private PythonIndentation() {}

  /**
   * Calculates the indentation of the provided spaces, taking the following rules into account:
   *
   * <p>"Tabs are replaced (from left to right) by one to eight spaces such that the total number of
   * characters up to and including the replacement is a multiple of eight [...]"
   *
   * @param spaces the leading whitespace of a line
   * @return the indentation width
   * @see <a href="https://docs.python.org/3.1/reference/lexical_analysis.html#indentation">Python
   *     indentation rules</a>
   */
  static int getIndentationCount(final String spaces) {
    int count = 0;
    for (final char ch : spaces.toCharArray()) {
      if (ch == TAB) {
        count += TAB_WIDTH - (count % TAB_WIDTH);
      } else {
        count++;
      }
    }
    return count;
  }
}
