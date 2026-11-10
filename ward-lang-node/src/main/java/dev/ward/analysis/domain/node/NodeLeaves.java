package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Flattens a shared syntax tree into its tokens in source order, without comments. */
final class NodeLeaves {
  /** The kind of a comment node, which holds no code. */
  private static final String COMMENT = "comment";

  private static final String TEXT = "text";

  private NodeLeaves() {}

  /** Returns the terminal nodes under {@code root} in source order, skipping comments. */
  static List<SyntaxNode> of(final SyntaxNode root) {
    final List<SyntaxNode> leaves = new ArrayList<>();
    collect(root, leaves);
    return leaves;
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> leaves) {
    if (COMMENT.equals(node.kind())) {
      return;
    }
    if (node.fields().containsKey(TEXT)) {
      leaves.add(node);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, leaves);
    }
  }

  /** The first direct child of the kind, or null. */
  static SyntaxNode child(final SyntaxNode node, final String kind) {
    return node.children().stream()
        .filter(child -> kind.equals(child.kind()))
        .findFirst()
        .orElse(null);
  }

  static boolean hasChild(final SyntaxNode node, final String kind) {
    return child(node, kind) != null;
  }

  /** The text of a token. */
  static String text(final SyntaxNode leaf) {
    return leaf.fields().getOrDefault("text", "");
  }

  /** The region from the first token of {@code from} to the last token of {@code to}. */
  static Span site(final SyntaxNode from, final SyntaxNode to) {
    final Span first = of(from).get(0).span();
    final Span last = to.span();
    return new Span(
        first.startLine(),
        first.startColumn(),
        last.endLine(),
        last.endColumn(),
        first.startOffset(),
        last.endOffset());
  }
}
