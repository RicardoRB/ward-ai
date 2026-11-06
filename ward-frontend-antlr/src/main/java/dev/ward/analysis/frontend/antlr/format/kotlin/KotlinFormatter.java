package dev.ward.analysis.frontend.antlr.format.kotlin;

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
import java.util.Locale;

/**
 * Formats Kotlin in Ward's style: four-space indentation, eight-column continuation for argument
 * and parameter lists, braces on the same line, and whitespace only. Ward's style is not ktlint's.
 */
public final class KotlinFormatter implements SourceFormatter {

  /** The option for the printed line width, in columns. */
  public static final String LINE_WIDTH = "line-width";

  /** The option for the indent character: {@code space} or {@code tab}. */
  public static final String INDENT_STYLE = "indent-style";

  /** The option for the width of one indent level, in columns. */
  public static final String INDENT_WIDTH = "indent-width";

  /** The option for the most blank lines kept in a row. */
  public static final String MAX_BLANK_LINES = "max-blank-lines";

  /** The option for the indent of a continuation line, in columns. */
  public static final String CONTINUATION_INDENT = "continuation-indent-size";

  private static final String SPACE_STYLE = "space";
  private static final String TAB_STYLE = "tab";

  @Override
  public AnalysisLanguage language() {
    return AnalysisLanguage.KOTLIN;
  }

  @Override
  public List<FormatOption> options() {
    return List.of(
        new FormatOption(LINE_WIDTH, FormatOptionType.INTEGER, "100", List.of()),
        new FormatOption(
            INDENT_STYLE, FormatOptionType.TEXT, SPACE_STYLE, List.of(SPACE_STYLE, TAB_STYLE)),
        new FormatOption(INDENT_WIDTH, FormatOptionType.INTEGER, "4", List.of()),
        new FormatOption(MAX_BLANK_LINES, FormatOptionType.INTEGER, "1", List.of()),
        new FormatOption(CONTINUATION_INDENT, FormatOptionType.INTEGER, "8", List.of()));
  }

  @Override
  public FormatResult format(final String source, final FormatOptions options) {
    final KotlinParse parse = KotlinParse.of(source);
    if (parse.errorLine().isPresent()) {
      return new FormatResult.Refused("input has a syntax error", parse.errorLine().getAsInt());
    }
    final int indentWidth = options.integer(INDENT_WIDTH);
    final int levels = Math.max(1, options.integer(CONTINUATION_INDENT) / indentWidth);
    final Document document =
        new KotlinLayout(parse, options.integer(MAX_BLANK_LINES), levels).build();
    final PrintStyle style =
        new PrintStyle(
            options.integer(LINE_WIDTH),
            indentStyle(options.get(INDENT_STYLE)),
            indentWidth,
            EndOfLine.LF);
    final String printed = new DocPrinter(style).print(document);
    return printed.equals(source)
        ? new FormatResult.Unchanged()
        : new FormatResult.Formatted(printed);
  }

  private static IndentStyle indentStyle(final String value) {
    return TAB_STYLE.equals(value.toLowerCase(Locale.ROOT)) ? IndentStyle.TAB : IndentStyle.SPACE;
  }
}
