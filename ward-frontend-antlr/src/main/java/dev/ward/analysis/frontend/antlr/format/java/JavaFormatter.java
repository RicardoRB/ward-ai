package dev.ward.analysis.frontend.antlr.format.java;

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
import java.util.logging.Logger;

/**
 * Formats Java source in Ward style. It changes only line breaks, indentation and spaces: every
 * token and comment is written as it was, and {@code FormatSafety} checks that before any write.
 */
public final class JavaFormatter implements SourceFormatter {

  private static final String INDENT_WIDTH = "indent-width";
  private static final String CONTINUATION_INDENT_SIZE = "continuation-indent-size";
  private static final String MAX_BLANK_LINES = "max-blank-lines";

  private static final Logger LOG = Logger.getLogger(JavaFormatter.class.getName());
  private static final int LINE_WIDTH = 100;
  private static final String DEFAULT_INDENT = "4";
  private static final String DEFAULT_CONTINUATION = "8";
  private static final String DEFAULT_BLANK_LINES = "1";
  private static final String SYNTAX_REASON = "syntax error";

  @Override
  public AnalysisLanguage language() {
    return AnalysisLanguage.JAVA;
  }

  @Override
  public List<FormatOption> options() {
    return List.of(
        new FormatOption(INDENT_WIDTH, FormatOptionType.INTEGER, DEFAULT_INDENT, List.of()),
        new FormatOption(
            CONTINUATION_INDENT_SIZE, FormatOptionType.INTEGER, DEFAULT_CONTINUATION, List.of()),
        new FormatOption(
            MAX_BLANK_LINES, FormatOptionType.INTEGER, DEFAULT_BLANK_LINES, List.of()));
  }

  @Override
  public FormatResult format(final String source, final FormatOptions options) {
    final JavaFormatParse parsed = JavaFormatParse.parse(source);
    if (parsed.errorLine().isPresent()) {
      LOG.fine(() -> "java format refused: syntax error at line " + parsed.errorLine().getAsInt());
      return new FormatResult.Refused(SYNTAX_REASON, parsed.errorLine().getAsInt());
    }
    final int indent = options.integer(INDENT_WIDTH);
    final PrintStyle style = new PrintStyle(LINE_WIDTH, IndentStyle.SPACE, indent, EndOfLine.LF);
    final int continuationLevels = options.integer(CONTINUATION_INDENT_SIZE) / indent;
    final JavaDocBuilder builder =
        new JavaDocBuilder(
            JavaAtom.of(parsed.tokens()),
            parsed.facts(),
            options.integer(MAX_BLANK_LINES),
            continuationLevels);
    final String printed = new DocPrinter(style).print(builder.build());
    return printed.equals(source)
        ? new FormatResult.Unchanged()
        : new FormatResult.Formatted(printed);
  }
}
