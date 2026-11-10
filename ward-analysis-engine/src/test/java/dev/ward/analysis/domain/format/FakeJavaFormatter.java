package dev.ward.analysis.domain.format;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.ArrayList;
import java.util.List;

/**
 * A stand-in Java formatter for the fixture harness. It re-indents each line by its brace depth,
 * trims line ends, and collapses blank runs to one line, all through {@link Document} and {@link
 * DocPrinter}.
 */
final class FakeJavaFormatter implements SourceFormatter {

  private static final String INDENT_WIDTH = "indent-width";
  private static final int LINE_WIDTH = 100;

  @Override
  public AnalysisLanguage language() {
    return AnalysisLanguage.JAVA;
  }

  @Override
  public List<FormatOption> options() {
    return List.of(new FormatOption(INDENT_WIDTH, FormatOptionType.INTEGER, "4", List.of()));
  }

  @Override
  public FormatResult format(final String source, final FormatOptions options) {
    final List<String> lines = new ArrayList<>(List.of(source.split("\n", -1)));
    if (!lines.isEmpty() && lines.getLast().isEmpty()) {
      lines.removeLast();
    }
    final Document doc = build(lines);
    final PrintStyle style =
        new PrintStyle(LINE_WIDTH, IndentStyle.SPACE, options.integer(INDENT_WIDTH), EndOfLine.LF);
    final String printed = new DocPrinter(style).print(doc);
    return printed.equals(source)
        ? new FormatResult.Unchanged()
        : new FormatResult.Formatted(printed);
  }

  private static Document build(final List<String> lines) {
    final List<Document> parts = new ArrayList<>();
    int depth = 0;
    boolean blank = false;
    boolean first = true;
    for (final String raw : lines) {
      final String content = raw.strip();
      if (content.isEmpty()) {
        blank = !first;
        continue;
      }
      if (content.startsWith("}")) {
        depth = Math.max(0, depth - 1);
      }
      if (!first) {
        if (blank) {
          parts.add(Document.hardLine());
        }
        parts.add(breakAt(depth));
      }
      parts.add(Document.text(content));
      if (content.endsWith("{")) {
        depth++;
      }
      first = false;
      blank = false;
    }
    if (!first) {
      parts.add(Document.hardLine());
    }
    return Document.concat(parts.toArray(Document[]::new));
  }

  private static Document breakAt(final int depth) {
    Document line = Document.hardLine();
    for (int level = 0; level < depth; level++) {
      line = Document.indent(line);
    }
    return line;
  }
}
