package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** The token leaves of a syntax tree, read by the magic-number check for its roles. */
final class NodeMagicNumberTokens {

  private NodeMagicNumberTokens() {}

  /** Whether the node is a token leaf with the given text. */
  static boolean isToken(final SyntaxNode node, final String token) {
    return isLeaf(node) && token.equals(NodeSyntax.text(node));
  }

  static boolean isLeaf(final SyntaxNode node) {
    return node.children().isEmpty();
  }

  /** The text of the child's previous sibling when that sibling is a token, or empty. */
  static String tokenBefore(final SyntaxNode parent, final SyntaxNode child) {
    final List<SyntaxNode> parts = parent.children();
    final int index = parts.indexOf(child);
    return index > 0 && isLeaf(parts.get(index - 1)) ? NodeSyntax.text(parts.get(index - 1)) : "";
  }

  /** The text of the first leaf under the node, or empty. */
  static String firstLeaf(final SyntaxNode node) {
    return NodeSyntax.leaves(node).stream().findFirst().orElse("");
  }
}
