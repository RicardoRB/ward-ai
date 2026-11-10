package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Tree helpers shared by the TypeScript and JavaScript style checks. */
final class NodeSyntax {

  private static final String IDENTIFIER = "Identifier";
  private static final String COMMENT = "comment";

  private NodeSyntax() {}

  /** The children that are syntax rules or tokens, not comments, in source order. */
  static List<SyntaxNode> code(final SyntaxNode node) {
    return node.children().stream().filter(child -> !COMMENT.equals(child.kind())).toList();
  }

  /** The source text of the node, without whitespace or comments. */
  static String text(final SyntaxNode node) {
    final StringBuilder out = new StringBuilder();
    appendText(node, out);
    return out.toString();
  }

  private static void appendText(final SyntaxNode node, final StringBuilder out) {
    if (COMMENT.equals(node.kind())) {
      return;
    }
    if (node.children().isEmpty()) {
      out.append(node.fields().getOrDefault("text", ""));
      return;
    }
    node.children().forEach(child -> appendText(child, out));
  }

  /** The text of each leaf token under the node, in source order. */
  static List<String> leaves(final SyntaxNode node) {
    final List<String> out = new ArrayList<>();
    collectLeaves(node, out);
    return out;
  }

  private static void collectLeaves(final SyntaxNode node, final List<String> out) {
    if (node.children().isEmpty()) {
      out.add(node.fields().getOrDefault("text", ""));
      return;
    }
    node.children().forEach(child -> collectLeaves(child, out));
  }

  /** The first identifier under the node, depth first, if there is one. */
  static Optional<SyntaxNode> firstIdentifier(final SyntaxNode node) {
    if (IDENTIFIER.equals(node.kind())) {
      return Optional.of(node);
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<SyntaxNode> found = firstIdentifier(child);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /** The first token of a node that has text, in source order. */
  static SyntaxNode firstLeaf(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      return node;
    }
    for (final SyntaxNode child : node.children()) {
      if (child.span().startOffset() < child.span().endOffset()) {
        return firstLeaf(child);
      }
    }
    return node;
  }
}
