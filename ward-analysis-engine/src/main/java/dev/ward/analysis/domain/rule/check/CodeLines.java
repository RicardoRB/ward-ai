package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/**
 * The lines of a file with the text of its string literals and comments blanked, so that a pattern
 * for code does not match a brace or a semicolon that is written inside a string (#567). Every
 * line keeps its length, so a column in a blanked line is the column in the source.
 */
final class CodeLines {
  private static final String TEXT = "text";
  private static final char BLANK = ' ';

  private CodeLines() {}

  /** Returns the lines of {@code file}, blanking the literals and comments of {@code root}. */
  static List<String> of(final SyntaxNode root, final SourceFile file) {
    final List<StringBuilder> lines = new ArrayList<>();
    file.lines().forEach(line -> lines.add(new StringBuilder(line)));
    blank(root, lines);
    return lines.stream().map(StringBuilder::toString).toList();
  }

  private static void blank(final SyntaxNode node, final List<StringBuilder> lines) {
    if (node.children().isEmpty() && isText(node)) {
      blank(node.span(), node.fields().get(TEXT), lines);
    }
    node.children().forEach(child -> blank(child, lines));
  }

  private static boolean isText(final SyntaxNode node) {
    final String text = node.fields().getOrDefault(TEXT, "");
    return SyntaxWalk.COMMENT_KIND.equals(node.kind())
        || text.startsWith("\"")
        || text.startsWith("'")
        || text.startsWith("#\"");
  }

  /** Blanks the lines the text covers: the first from the start column, the others whole. */
  private static void blank(final Span span, final String text, final List<StringBuilder> lines) {
    final int count = text.split("\n", -1).length;
    final int last = Math.min(span.startLine() + count - 1, lines.size());
    for (int number = span.startLine(); number <= last; number++) {
      final StringBuilder line = lines.get(number - 1);
      final int from = number == span.startLine() ? Math.max(span.startColumn() - 1, 0) : 0;
      final boolean ends = number == span.startLine() + count - 1;
      final int to = ends && count == 1 ? from + text.length() : line.length();
      for (int column = from; column < Math.min(to, line.length()); column++) {
        line.setCharAt(column, BLANK);
      }
    }
  }
}
