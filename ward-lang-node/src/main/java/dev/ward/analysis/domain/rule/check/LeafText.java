package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

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

  /** The leaves below a node, in source order. */
  static List<SyntaxNode> leaves(final SyntaxNode node) {
    final List<SyntaxNode> leaves = new ArrayList<>();
    collectLeaves(node, leaves);
    return leaves;
  }

  private static void collectLeaves(final SyntaxNode node, final List<SyntaxNode> leaves) {
    if (node.children().isEmpty()) {
      leaves.add(node);
    } else {
      node.children().forEach(child -> collectLeaves(child, leaves));
    }
  }

  /** Moves the position past {@code count} characters of the text from {@code from}. */
  static void advanceBy(final int[] position, final String text, final int from, final int count) {
    for (int offset = 0; offset < count; offset++) {
      advance(position, text.charAt(from + offset));
    }
  }

  /** Moves a line and column past one character, starting a new line after a line terminator. */
  static void advance(final int[] position, final char c) {
    if (c == '\n' || c == '\r' || c == ' ' || c == ' ') {
      position[0]++;
      position[1] = 1;
    } else {
      position[1]++;
    }
  }
}
