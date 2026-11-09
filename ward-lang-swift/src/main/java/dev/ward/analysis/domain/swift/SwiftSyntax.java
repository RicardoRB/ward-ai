package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;

/** The syntax-tree reads that the Swift summarizer and its declaration and use scans share. */
final class SwiftSyntax {

  private SwiftSyntax() {}

  /** The first direct child of {@code node} with {@code kind}, if there is one. */
  static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(c -> kind.equals(c.kind())).findFirst();
  }

  /** The source text of a subtree, concatenated from its terminals in order. */
  static String textOf(final SyntaxNode node) {
    final StringBuilder text = new StringBuilder();
    appendText(node, text);
    return text.toString();
  }

  private static void appendText(final SyntaxNode node, final StringBuilder text) {
    final String token = node.fields().get("text");
    if (token != null) {
      text.append(token);
    }
    for (final SyntaxNode child : node.children()) {
      appendText(child, text);
    }
  }
}
