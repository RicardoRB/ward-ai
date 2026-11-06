package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads the shared syntax tree: child lookup by kind, and token text without comments. */
final class Syntax {
  private static final String COMMENT = "comment";
  private static final String TEXT = "text";

  private Syntax() {}

  /** Returns the direct children of {@code node} whose kind is {@code kind}. */
  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }

  /** Returns the first direct child of {@code kind}. */
  static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).findFirst();
  }

  /** Returns the first descendant of {@code kind}, searching depth first. */
  static Optional<SyntaxNode> find(final SyntaxNode node, final String kind) {
    if (kind.equals(node.kind())) {
      return Optional.of(node);
    }
    for (final SyntaxNode child : node.children()) {
      final Optional<SyntaxNode> found = find(child, kind);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /** Returns the text of a terminal node, or an empty string for a rule node. */
  static String token(final SyntaxNode node) {
    return node.fields().getOrDefault(TEXT, "");
  }

  /** Returns whether a direct child is a terminal with exactly {@code text}. */
  static boolean hasToken(final SyntaxNode node, final String text) {
    return node.children().stream()
        .anyMatch(child -> child.children().isEmpty() && text.equals(token(child)));
  }

  /** Returns the source tokens under {@code node} joined without spacing or comments. */
  static String text(final SyntaxNode node) {
    final StringBuilder text = new StringBuilder();
    append(node, text);
    return text.toString();
  }

  /** Returns the leaf tokens under {@code node}, in order, without comments. */
  static List<String> tokens(final SyntaxNode node) {
    final List<String> tokens = new ArrayList<>();
    collect(node, tokens);
    return tokens;
  }

  private static void append(final SyntaxNode node, final StringBuilder text) {
    if (COMMENT.equals(node.kind())) {
      return;
    }
    if (node.children().isEmpty()) {
      text.append(token(node));
      return;
    }
    node.children().forEach(child -> append(child, text));
  }

  private static void collect(final SyntaxNode node, final List<String> tokens) {
    if (COMMENT.equals(node.kind())) {
      return;
    }
    if (node.children().isEmpty()) {
      tokens.add(token(node));
      return;
    }
    node.children().forEach(child -> collect(child, tokens));
  }
}
