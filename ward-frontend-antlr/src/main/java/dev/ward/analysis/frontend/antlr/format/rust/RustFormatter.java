package dev.ward.analysis.frontend.antlr.format.rust;

import dev.ward.analysis.domain.format.DocPrinter;
import dev.ward.analysis.domain.format.Document;
import dev.ward.analysis.domain.format.EndOfLine;
import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.format.FormatOptionType;
import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.IndentStyle;
import dev.ward.analysis.domain.format.PrintStyle;
import dev.ward.analysis.domain.format.SourceFormatter;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;

/**
 * Formats Rust like rustfmt's defaults: four-space indents, braces on the line of their item,
 * spaces by token pair, a continued statement indented one level and blank lines collapsed. Only
 * whitespace changes, so unlike rustfmt it adds no trailing comma, breaks no long line and reorders
 * no import; {@link dev.ward.analysis.domain.format.FormatSafety} checks the result before
 * anything is written.
 */
public final class RustFormatter implements SourceFormatter {
  /** Option key for the line width the printer fills. */
  static final String LINE_WIDTH = "line-width";

  /** Option key for the indent unit: {@code space} or {@code tab}. */
  static final String INDENT_STYLE = "indent-style";

  /** Option key for the number of spaces in one indent level. */
  static final String INDENT_WIDTH = "indent-width";

  /** Option key for the most blank lines kept in a row. */
  static final String MAX_BLANK_LINES = "max-blank-lines";

  private static final String SPACE_STYLE = "space";
  private static final String TAB_STYLE = "tab";
  private static final String DEFAULT_LINE_WIDTH = "100";
  private static final String DEFAULT_INDENT_WIDTH = "4";
  private static final String DEFAULT_MAX_BLANK_LINES = "1";
  private static final String LF = "\n";
  private static final String TRAILING_NEWLINES = "\n+$";
  private static final List<FormatOption> OPTIONS =
      List.of(
          new FormatOption(LINE_WIDTH, FormatOptionType.INTEGER, DEFAULT_LINE_WIDTH, List.of()),
          new FormatOption(
              INDENT_STYLE,
              FormatOptionType.TEXT,
              SPACE_STYLE,
              List.of(SPACE_STYLE, TAB_STYLE)
          ),
          new FormatOption(INDENT_WIDTH, FormatOptionType.INTEGER, DEFAULT_INDENT_WIDTH, List.of()),
          new FormatOption(
              MAX_BLANK_LINES,
              FormatOptionType.INTEGER,
              DEFAULT_MAX_BLANK_LINES,
              List.of()
          )
      );

  /**
   * The language this formatter handles.
   *
   * @return Rust
   */
  @Override
  public AnalysisLanguage language() {
    return AnalysisLanguage.RUST;
  }

  /**
   * The options this formatter declares: line width, indent style and width, and the blank line
   * limit.
   *
   * @return the declared options
   */
  @Override
  public List<FormatOption> options() {
    return OPTIONS;
  }

  /**
   * Lays out the source in one pass. Output always uses LF line endings and ends with exactly one
   * newline. The layout reads only the source's line breaks and never moves one, so a second pass
   * gives the same text. The result is not checked here; the caller runs the safety checks.
   *
   * @param source the Rust source
   * @param options the option values
   * @return the formatted text, or {@code Unchanged} when it equals the source
   */
  @Override
  public FormatResult format(final String source, final FormatOptions options) {
    final Document document =
        new RustDocBuilder(RustParsing.of(source)).build(options.integer(MAX_BLANK_LINES));
    final PrintStyle style =
        new PrintStyle(
            options.integer(LINE_WIDTH),
            TAB_STYLE.equals(options.get(INDENT_STYLE)) ? IndentStyle.TAB : IndentStyle.SPACE,
            options.integer(INDENT_WIDTH),
            EndOfLine.LF
        );
    final String text = finalNewline(new DocPrinter(style).print(document));
    return text.equals(source) ? new FormatResult.Unchanged() : new FormatResult.Formatted(text);
  }

  private static String finalNewline(final String text) {
    final String body = text.replaceAll(TRAILING_NEWLINES, "");
    return body.isEmpty() ? body : body + LF;
  }
}
