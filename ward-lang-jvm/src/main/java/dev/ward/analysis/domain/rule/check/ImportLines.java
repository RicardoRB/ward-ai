package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import java.util.List;

/** The lines of a unit, for the checks that tell a blank line from code and comments. */
final class ImportLines {

  private static final String LINE_BREAK = "\r\n|\n|\r";

  private ImportLines() {}

  /** Returns the lines of {@code unit}; empty when the unit or its text is missing. */
  static List<String> of(final SourceUnit unit) {
    if (unit == null || unit.text().isEmpty()) {
      return List.of();
    }
    return List.of(unit.text().split(LINE_BREAK, -1));
  }

  /** Counts the blank lines strictly between the 1-based lines {@code after} and {@code before}. */
  static int blankBetween(final List<String> lines, final int after, final int before) {
    int blank = 0;
    for (int line = after + 1; line < before; line++) {
      if (lines.get(line - 1).isBlank()) {
        blank++;
      }
    }
    return blank;
  }
}
