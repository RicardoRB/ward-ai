package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;

/** Builds small syntax trees for the metric tests, so each test states its tree in place. */
final class Trees {
  private static final int NAME_COLUMN = 5;

  private Trees() {}

  static SyntaxNode node(
      final String kind, final int startLine, final int endLine, final SyntaxNode... children) {
    return new SyntaxNode(kind, span(startLine, 1, endLine, 1), Map.of(), List.of(children));
  }

  static SyntaxNode token(final String kind, final String text, final int line, final int column) {
    return new SyntaxNode(
        kind,
        span(line, column, line, column + text.length() - 1),
        Map.of("text", text),
        List.of());
  }

  static SyntaxNode named(final String kind, final String name, final int line) {
    return node(kind, line, line, token("NAME", name, line, NAME_COLUMN));
  }

  static Span span(
      final int startLine, final int startColumn, final int endLine, final int endColumn) {
    return new Span(startLine, startColumn, endLine, endColumn, 0, 0);
  }
}
