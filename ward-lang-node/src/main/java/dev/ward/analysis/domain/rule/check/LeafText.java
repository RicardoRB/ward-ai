package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;

/**
 * The source text of a syntax node, read from its leaves, for the TypeScript and JavaScript checks.
 */
final class LeafText {

  private LeafText() {}

  /** The text of the leaves below a node, in order; a leaf's own text is its text. */
  static String of(final SyntaxNode node) {
    final StringBuilder text = new StringBuilder();
    append(node, text);
    return text.toString();
  }

  private static void append(final SyntaxNode node, final StringBuilder text) {
    if (node.children().isEmpty()) {
      text.append(node.fields().getOrDefault("text", ""));
      return;
    }
    node.children().forEach(child -> append(child, text));
  }
}
