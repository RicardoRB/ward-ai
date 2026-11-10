package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;

/**
 * Lays tokens out on one line from left to right, so each node starts where the text before it
 * ends. A test keys its type facts by a node's start with {@link #startOf}.
 */
final class Layout {

  private static final int LINE = 1;
  private static final int FIRST_COLUMN = 1;

  private int column = FIRST_COLUMN;

  /** A token with text, placed at the current column. */
  SyntaxNode token(final String kind, final String text) {
    final int start = column;
    column += text.length();
    return new SyntaxNode(
        kind, new Span(LINE, start, LINE, column, start, column), Map.of("text", text), List.of());
  }

  /** A node whose span runs from its first child's start to its last child's end. */
  static SyntaxNode node(final String kind, final SyntaxNode... children) {
    final Span first = children[0].span();
    final Span last = children[children.length - 1].span();
    return new SyntaxNode(
        kind,
        new Span(
            first.startLine(),
            first.startColumn(),
            last.endLine(),
            last.endColumn(),
            first.startOffset(),
            last.endOffset()),
        Map.of(),
        List.of(children));
  }

  /** The key of the type facts that name {@code node}'s start. */
  static String startOf(final SyntaxNode node) {
    return node.span().startLine() + ":" + node.span().startColumn();
  }
}
