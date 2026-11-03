package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Test helpers that read comment nodes out of a syntax tree. */
final class SyntaxTrees {

  private SyntaxTrees() {}

  /** Returns every comment node below {@code root}, in source order. */
  static List<SyntaxNode> comments(final SyntaxNode root) {
    final List<SyntaxNode> comments = new ArrayList<>();
    collectComments(root, comments);
    return comments;
  }

  /** Returns the text field of {@code node}. */
  static String text(final SyntaxNode node) {
    return node.fields().get("text");
  }

  /** Returns a copy of {@code node} with every comment node removed, at every depth. */
  static SyntaxNode withoutComments(final SyntaxNode node) {
    final List<SyntaxNode> children =
        node.children().stream()
            .filter(child -> !SyntaxTreeMapper.COMMENT_KIND.equals(child.kind()))
            .map(SyntaxTrees::withoutComments)
            .toList();
    return new SyntaxNode(node.kind(), node.span(), node.fields(), children);
  }

  /** Kinds, texts and nesting only: comments move later lines, so spans are not compared. */
  static String shape(final SyntaxNode node) {
    final StringBuilder out = new StringBuilder();
    appendShape(node, out);
    return out.toString();
  }

  private static void collectComments(final SyntaxNode node, final List<SyntaxNode> comments) {
    if (SyntaxTreeMapper.COMMENT_KIND.equals(node.kind())) {
      comments.add(node);
    }
    node.children().forEach(child -> collectComments(child, comments));
  }

  private static void appendShape(final SyntaxNode node, final StringBuilder out) {
    out.append(node.kind()).append(node.fields()).append('[');
    node.children().forEach(child -> appendShape(child, out));
    out.append(']');
  }
}
