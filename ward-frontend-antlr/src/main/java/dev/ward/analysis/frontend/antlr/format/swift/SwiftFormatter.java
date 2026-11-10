package dev.ward.analysis.frontend.antlr.format.swift;

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
import org.antlr.v4.runtime.Token;

/**
 * Formats Swift in Ward's style: braces on the line of their statement, one switch case per line at
 * the switch's indent, long bracket lists broken one item per line, and operators, commas and
 * keywords spaced by token pair. Only whitespace changes; {@link
 * dev.ward.analysis.domain.format.FormatSafety} checks the result before anything is written.
 */
public final class SwiftFormatter implements SourceFormatter {

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
              INDENT_STYLE, FormatOptionType.TEXT, SPACE_STYLE, List.of(SPACE_STYLE, TAB_STYLE)),
          new FormatOption(INDENT_WIDTH, FormatOptionType.INTEGER, DEFAULT_INDENT_WIDTH, List.of()),
          new FormatOption(
              MAX_BLANK_LINES, FormatOptionType.INTEGER, DEFAULT_MAX_BLANK_LINES, List.of()));

  /**
   * The language this formatter handles.
   *
   * @return Swift
   */
  @Override
  public AnalysisLanguage language() {
    return AnalysisLanguage.SWIFT;
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
   * Lays out the source. Output always uses LF line endings and ends with exactly one newline. The
   * result is not checked here; the caller runs the safety checks.
   *
   * @param source the Swift source
   * @param options the option values
   * @return the formatted text, or {@code Unchanged} when it equals the source
   */
  @Override
  public FormatResult format(final String source, final FormatOptions options) {
    final List<Token> tokens =
        SwiftLexing.significant(SwiftLexing.filled(SwiftLexing.lexer(source)).getTokens());
    final Document document =
        new SwiftDocBuilder(source, tokens, options.integer(MAX_BLANK_LINES)).build();
    final PrintStyle style =
        new PrintStyle(
            options.integer(LINE_WIDTH),
            indentStyle(options.get(INDENT_STYLE)),
            options.integer(INDENT_WIDTH),
            EndOfLine.LF);
    final String text = finalNewline(new DocPrinter(style).print(document));
    return text.equals(source) ? new FormatResult.Unchanged() : new FormatResult.Formatted(text);
  }

  private static IndentStyle indentStyle(final String value) {
    return TAB_STYLE.equals(value) ? IndentStyle.TAB : IndentStyle.SPACE;
  }

  private static String finalNewline(final String text) {
    final String body = text.replaceAll(TRAILING_NEWLINES, "");
    return body.isEmpty() ? body : body + LF;
  }
}
