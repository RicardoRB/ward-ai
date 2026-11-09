package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Tree helpers shared by the TypeScript and JavaScript style checks. */
final class NodeSyntax {

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
}
