package dev.ward.analysis.domain.node;

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
}
