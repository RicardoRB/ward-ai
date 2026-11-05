package dev.ward.analysis.domain.format;

import java.util.Objects;

/**
 * The layout settings the printer applies. Language formatters never read these; they only say
 * where breaks may go.
 *
 * @param lineWidth the widest line the printer aims for, in characters
 * @param indentStyle how an indentation level is written
 * @param indentWidth the width of one indentation level in spaces, also the column width of a tab
 * @param endOfLine the line ending written at every break
 */
public record PrintStyle(
    int lineWidth, IndentStyle indentStyle, int indentWidth, EndOfLine endOfLine) {

  private static final int MINIMUM = 1;

  /**
   * Checks the settings.
   *
   * @throws NullPointerException if the indent style or the end of line is missing
   * @throws IllegalArgumentException if the line width or the indent width is below one
   */
  public PrintStyle {
    Objects.requireNonNull(indentStyle, "indentStyle");
    Objects.requireNonNull(endOfLine, "endOfLine");
    if (lineWidth < MINIMUM || indentWidth < MINIMUM) {
      throw new IllegalArgumentException("line width and indent width must be at least 1");
    }
  }
}
