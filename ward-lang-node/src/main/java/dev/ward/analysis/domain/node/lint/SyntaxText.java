package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads the text and the tokens of TypeScript syntax nodes for the typescript-eslint checks. */
final class SyntaxText {

  private static final String TEXT = "text";
  private static final String EOF = "<EOF>";

  private SyntaxText() {}

  /** Returns the text of a node: its leaves' text joined without separators. */
  static String of(final SyntaxNode node) {
    final StringBuilder out = new StringBuilder();
    for (final SyntaxNode leaf : leaves(node)) {
      out.append(leaf.fields().getOrDefault(TEXT, ""));
    }
    return out.toString();
  }

  /** Reports whether a node is the token {@code text}, a leaf of the grammar. */
  static boolean isToken(final SyntaxNode node, final String text) {
    return node != null && node.children().isEmpty() && text.equals(node.fields().get(TEXT));
  }

  /** Reports whether a node has a direct child that is the token {@code text}. */
  static boolean hasToken(final SyntaxNode node, final String text) {
    return node.children().stream().anyMatch(child -> isToken(child, text));
  }

  /** Returns the direct children of {@code kind}, in order. */
  static List<SyntaxNode> childrenOf(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }

  /** Returns the nodes of {@code kind} in {@code root}, in source order. */
  static List<SyntaxNode> nodesOf(final SyntaxNode root, final String kind) {
    final List<SyntaxNode> out = new ArrayList<>();
    collect(root, kind, out);
    return out;
  }

  /** Returns the last child of a node, or null when it has none. */
  static SyntaxNode last(final SyntaxNode node) {
    return node.children().isEmpty() ? null : node.children().get(node.children().size() - 1);
  }

  private static void collect(
      final SyntaxNode node, final String kind, final List<SyntaxNode> out) {
    if (kind.equals(node.kind())) {
      out.add(node);
    }
    node.children().forEach(child -> collect(child, kind, out));
  }

  /** Returns the leaves of a node in source order. */
  static List<SyntaxNode> leaves(final SyntaxNode node) {
    final List<SyntaxNode> out = new ArrayList<>();
    gather(node, out);
    return out;
  }

  private static void gather(final SyntaxNode node, final List<SyntaxNode> out) {
    if (node.children().isEmpty()) {
      if (!EOF.equals(node.fields().get(TEXT))) {
        out.add(node);
      }
      return;
    }
    node.children().forEach(child -> gather(child, out));
  }
}
