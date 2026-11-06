package dev.ward.domain.format;

import dev.ward.analysis.domain.ir.AnalysisLanguage;

/** Ward's built-in formatting defaults, used for every option that no configuration file sets. */
public final class FormatDefaults {

  /** The line width used when {@code max_line_length} is not set. */
  public static final int LINE_WIDTH = 100;

  /** The blank lines kept between statements when {@code ward_max_blank_lines} is not set. */
  public static final int MAX_BLANK_LINES = 1;

  private static final int DEFAULT_INDENT = 4;
  private static final int WEB_INDENT = 2;

  private FormatDefaults() {}

  /**
   * Returns the indent width of a language when {@code indent_size} is not set.
   *
   * @param language the language
   * @return 2 for TypeScript and JavaScript, 4 for the other languages
   */
  public static int indentSize(final AnalysisLanguage language) {
    return switch (language) {
      case TYPESCRIPT, JAVASCRIPT -> WEB_INDENT;
      default -> DEFAULT_INDENT;
    };
  }
}
