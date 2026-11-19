package dev.ward.analysis.frontend.antlr.format.csharp;

import dev.ward.analysis.domain.format.DocPrinter;
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
import java.util.logging.Logger;

/**
 * Formats C# like {@code dotnet format whitespace} at its defaults: four-space indentation, braces
 * on their own line, and whitespace only. Ward does not wrap long lines, as {@code dotnet format}
 * does not.
 */
public final class CSharpFormatter implements SourceFormatter {
  /** The option for the indent character: {@code space} or {@code tab}. */
  public static final String INDENT_STYLE = "indent-style";

  /** The option for the width of one indent level, in columns. */
  public static final String INDENT_WIDTH = "indent-width";

  private static final Logger LOGGER = Logger.getLogger(CSharpFormatter.class.getName());
  private static final String SPACE_STYLE = "space";
  private static final String TAB_STYLE = "tab";
  private static final int UNUSED_LINE_WIDTH = 10_000;

  @Override
  public AnalysisLanguage language() {
    return AnalysisLanguage.CSHARP;
  }

  @Override
  public List<FormatOption> options() {
    return List.of(
        new FormatOption(
            INDENT_STYLE,
            FormatOptionType.TEXT,
            SPACE_STYLE,
            List.of(SPACE_STYLE, TAB_STYLE)
        ),
        new FormatOption(INDENT_WIDTH, FormatOptionType.INTEGER, "4", List.of())
    );
  }

  @Override
  public FormatResult format(final String source, final FormatOptions options) {
    final CSharpParse parse = CSharpParse.of(source);
    if (parse.errorLine().isPresent()) {
      return new FormatResult.Refused("input has a syntax error", parse.errorLine().getAsInt());
    }
    if (parse.skippedLine().isPresent()) {
      LOGGER.fine("refusing a C# file with a conditional branch the parser does not read");
      return new FormatResult.Refused(
          "a false conditional-compilation branch hides code from the formatter",
          parse.skippedLine().getAsInt()
      );
    }
    final PrintStyle style =
        new PrintStyle(
            UNUSED_LINE_WIDTH,
            indentStyle(options.get(INDENT_STYLE)),
            options.integer(INDENT_WIDTH),
            EndOfLine.LF
        );
    final String printed = new DocPrinter(style).print(new CSharpLayout(parse).build());
    return printed.equals(source)
        ? new FormatResult.Unchanged()
        : new FormatResult.Formatted(printed);
  }

  private static IndentStyle indentStyle(final String value) {
    return TAB_STYLE.equals(value.toLowerCase(Locale.ROOT)) ? IndentStyle.TAB : IndentStyle.SPACE;
  }
}
