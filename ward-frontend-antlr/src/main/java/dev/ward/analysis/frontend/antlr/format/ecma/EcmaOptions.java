package dev.ward.analysis.frontend.antlr.format.ecma;

import dev.ward.analysis.domain.format.EndOfLine;
import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.format.FormatOptionType;
import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.IndentStyle;
import dev.ward.analysis.domain.format.PrintStyle;
import java.util.List;

/**
 * The options the TypeScript and JavaScript formatters declare, and how they map to a print style.
 */
final class EcmaOptions {

  /** The longest line the printer fills before it breaks a group. */
  static final String LINE_WIDTH = "line-width";

  /** The spaces per indentation level. */
  static final String INDENT_WIDTH = "indent-width";

  /** The most blank lines kept in a run. */
  static final String MAX_BLANK_LINES = "max-blank-lines";

  /** The options both formatters declare, in declaration order. */
  static final List<FormatOption> DECLARED =
      List.of(
          new FormatOption(LINE_WIDTH, FormatOptionType.INTEGER, "100", List.of()),
          new FormatOption(INDENT_WIDTH, FormatOptionType.INTEGER, "2", List.of()),
          new FormatOption(MAX_BLANK_LINES, FormatOptionType.INTEGER, "1", List.of()));

  private EcmaOptions() {}

  /**
   * Maps the option values to the printer's style.
   *
   * @param options the resolved option values
   * @return the print style, with spaces and line feeds
   */
  static PrintStyle printStyle(final FormatOptions options) {
    return new PrintStyle(
        options.integer(LINE_WIDTH),
        IndentStyle.SPACE,
        options.integer(INDENT_WIDTH),
        EndOfLine.LF);
  }
}
