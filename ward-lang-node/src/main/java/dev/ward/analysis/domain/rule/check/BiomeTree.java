package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads the ancestors and children of a syntax node for the Biome lint checks. */
final class BiomeTree {

  private BiomeTree() {}

  /** Returns the ancestor {@code level} steps above the node, where 0 is the parent. */
  static SyntaxNode up(final List<SyntaxNode> ancestors, final int level) {
    return ancestors.get(ancestors.size() - 1 - level);
  }

  /** Tells whether the node is of this kind. */
  static boolean isKind(final SyntaxNode node, final String kind) {
    return kind.equals(node.kind());
  }

  /** Tells whether the node has a direct child of this kind; a token's kind is its quoted text. */
  static boolean hasChild(final SyntaxNode node, final String kind) {
    return node.children().stream().anyMatch(child -> child.kind().equals(kind));
  }
}
