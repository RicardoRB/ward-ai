package dev.ward.analysis.frontend.antlr.format.python;

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
import dev.ward.analysis.frontend.antlr.python.Python3Lexer;
import java.util.List;
import java.util.Optional;
import org.antlr.v4.runtime.Token;

/**
 * Formats Python in Ward style. Indentation follows the lexer's blocks, long lines break only
 * inside brackets, and every token, comment and backslash continuation is kept as written.
 */
public final class PythonFormatter implements SourceFormatter {

  private static final String INDENT_WIDTH = "indent-width";
  private static final String INDENT_STYLE = "indent-style";
  private static final String LINE_WIDTH = "line-width";
  private static final String MAX_BLANK_LINES = "max-blank-lines";
  private static final String DEFAULT_INDENT_WIDTH = "4";
  private static final String DEFAULT_LINE_WIDTH = "100";
  private static final String SPACE = "space";
  private static final String TAB = "tab";
  private static final String TAB_UNIT = "\t";
  private static final String DEFAULT_MAX_BLANK_LINES = "1";
  private static final String NEWLINE = "\n";

  @Override
  public AnalysisLanguage language() {
    return AnalysisLanguage.PYTHON;
  }

  @Override
  public List<FormatOption> options() {
    return List.of(
        new FormatOption(INDENT_WIDTH, FormatOptionType.INTEGER, DEFAULT_INDENT_WIDTH, List.of()),
        new FormatOption(INDENT_STYLE, FormatOptionType.TEXT, SPACE, List.of(SPACE, TAB)),
        new FormatOption(LINE_WIDTH, FormatOptionType.INTEGER, DEFAULT_LINE_WIDTH, List.of()),
        new FormatOption(
            MAX_BLANK_LINES, FormatOptionType.INTEGER, DEFAULT_MAX_BLANK_LINES, List.of()));
  }

  @Override
  public FormatResult format(final String source, final FormatOptions options) {
    final List<Token> tokens = PythonEntries.tokens(source);
    final Optional<Token> unknown =
        tokens.stream().filter(token -> token.getType() == Python3Lexer.UNKNOWN_CHAR).findFirst();
    if (unknown.isPresent()) {
      return new FormatResult.Refused("unknown character", unknown.get().getLine());
    }
    final int maxBlank = options.integer(MAX_BLANK_LINES);
    final boolean tabs = TAB.equals(options.get(INDENT_STYLE));
    final int indentWidth = options.integer(INDENT_WIDTH);
    final PrintStyle style =
        new PrintStyle(
            options.integer(LINE_WIDTH),
            tabs ? IndentStyle.TAB : IndentStyle.SPACE,
            indentWidth,
            EndOfLine.LF);
    final String indentUnit = tabs ? TAB_UNIT : " ".repeat(indentWidth);
    final List<PythonChunk> chunks =
        PythonBlankLines.apply(
            new PythonChunker(source, indentUnit, maxBlank).run(PythonEntries.of(source, tokens)),
            maxBlank);
    final String formatted = render(chunks, new DocPrinter(style));
    return formatted.equals(source)
        ? new FormatResult.Unchanged()
        : new FormatResult.Formatted(formatted);
  }

  private static String render(final List<PythonChunk> chunks, final DocPrinter printer) {
    final StringBuilder out = new StringBuilder();
    for (final PythonChunk chunk : chunks) {
      if (chunk.isBlank()) {
        out.append(NEWLINE.repeat(chunk.count()));
      } else if (chunk.isCode()) {
        out.append(printer.print(chunk.doc())).append(NEWLINE);
      } else {
        out.append(chunk.text()).append(NEWLINE);
      }
    }
    return out.toString();
  }
}
