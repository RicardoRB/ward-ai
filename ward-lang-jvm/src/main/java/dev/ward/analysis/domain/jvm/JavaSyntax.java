package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;

/** Reads the text of Java syntax nodes, skipping comments. */
final class JavaSyntax {
  /** The kind of a comment node in the shared syntax tree. */
  static final String COMMENT = "comment";

  private static final String TEXT = "text";

  private JavaSyntax() {}

  /** Returns the first direct child of {@code kind}, or fails when the grammar has none. */
  static SyntaxNode child(final SyntaxNode node, final String kind) {
    return node.children().stream()
        .filter(child -> kind.equals(child.kind()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(node.kind() + " has no " + kind));
  }

  /** Returns whether a direct child is a terminal with exactly {@code text}. */
  static boolean hasTerminal(final SyntaxNode node, final String text) {
    return node.children().stream()
        .anyMatch(child -> child.children().isEmpty() && text.equals(textOf(child)));
  }

  /** Returns the source text of every token under {@code node}, without comments or spacing. */
  static String dotted(final SyntaxNode node) {
    final StringBuilder text = new StringBuilder();
    appendTokens(node, text);
    return text.toString();
  }

  /** Returns the text of a terminal node, or an empty string for a rule node. */
  static String textOf(final SyntaxNode node) {
    return node.fields().getOrDefault(TEXT, "");
  }

  private static void appendTokens(final SyntaxNode node, final StringBuilder text) {
    if (COMMENT.equals(node.kind())) {
      return;
    }
    if (node.children().isEmpty()) {
      text.append(textOf(node));
      return;
    }
    for (final SyntaxNode child : node.children()) {
      appendTokens(child, text);
    }
  }
}
