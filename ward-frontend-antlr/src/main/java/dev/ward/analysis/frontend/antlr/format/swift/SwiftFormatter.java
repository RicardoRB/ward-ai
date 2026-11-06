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
 * Formats Swift in Ward's style: four-space indentation, braces on the line of their statement, one
 * switch case per line at the switch's indent, and long bracket lists broken one item per line.
 * Only whitespace changes; {@link dev.ward.analysis.domain.format.FormatSafety} checks the result
 * before anything is written.
 */
public final class SwiftFormatter implements SourceFormatter {

  /** Option key for the line width the printer fills. */
  static final String LINE_WIDTH = "line-width";

  /** Option key for the most blank lines kept in a row. */
  static final String MAX_BLANK_LINES = "max-blank-lines";

  private static final int INDENT_WIDTH = 4;
  private static final String DEFAULT_LINE_WIDTH = "100";
  private static final String DEFAULT_MAX_BLANK_LINES = "1";
  private static final String CR_LF = "\r\n";
  private static final List<FormatOption> OPTIONS =
      List.of(
          new FormatOption(LINE_WIDTH, FormatOptionType.INTEGER, DEFAULT_LINE_WIDTH, List.of()),
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
   * The options this formatter declares.
   *
   * @return the line width and the blank line limit
   */
  @Override
  public List<FormatOption> options() {
    return OPTIONS;
  }

  /**
   * Lays out the source. The result is not checked here; the caller runs the safety checks.
   *
   * @param source the Swift source
   * @param options the option values
   * @return the formatted text
   */
  @Override
  public FormatResult format(final String source, final FormatOptions options) {
    final List<Token> tokens =
        SwiftLexing.significant(SwiftLexing.filled(SwiftLexing.lexer(source)).getTokens());
    final Document document =
        new SwiftDocBuilder(source, tokens, options.integer(MAX_BLANK_LINES)).build();
    final PrintStyle style =
        new PrintStyle(
            options.integer(LINE_WIDTH), IndentStyle.SPACE, INDENT_WIDTH, endOfLine(source));
    final String text = new DocPrinter(style).print(document);
    return text.equals(source) ? new FormatResult.Unchanged() : new FormatResult.Formatted(text);
  }

  private static EndOfLine endOfLine(final String source) {
    return source.contains(CR_LF) ? EndOfLine.CRLF : EndOfLine.LF;
  }
}
