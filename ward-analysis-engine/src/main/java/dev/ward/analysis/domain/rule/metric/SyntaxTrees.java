package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Reads a syntax tree by node kind. Tokens are the leaves that carry a {@code text} field. */
final class SyntaxTrees {
  private static final String TEXT = "text";
  private static final String KIND_SEPARATOR = "\\|";

  private SyntaxTrees() {}

  /** Returns the source text of {@code node}, without any whitespace the tree does not keep. */
  static String text(final SyntaxNode node) {
    if (isToken(node)) {
      return node.fields().get(TEXT);
    }
    return node.children().stream().map(SyntaxTrees::text).collect(Collectors.joining());
  }

  /** Returns whether {@code node} is a token, a leaf of the tree. */
  static boolean isToken(final SyntaxNode node) {
    return node.fields().containsKey(TEXT);
  }

  /** Returns the kind of a token without the quotes the grammar puts around literal tokens. */
  static String tokenKind(final SyntaxNode node) {
    return node.kind().replace("'", "");
  }

  /**
   * Returns the first direct child of {@code node} whose kind is one of the {@code |}-separated
   * kinds.
   */
  static Optional<SyntaxNode> child(final SyntaxNode node, final String kinds) {
    final List<String> alternatives = List.of(kinds.split(KIND_SEPARATOR));
    return node.children().stream()
        .filter(child -> alternatives.contains(child.kind()))
        .findFirst();
  }

  /** Returns the direct children of {@code node} with {@code kind}. */
  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> child.kind().equals(kind)).toList();
  }

  /**
   * Returns the declared name held by a child of one of the {@code kind} alternatives, or empty.
   */
  static String nameOf(final SyntaxNode node, final String kind) {
    return kind.isEmpty() ? "" : child(node, kind).map(SyntaxTrees::text).orElse("");
  }

  /** Follows {@code path} from {@code node}, one child kind per step. */
  static List<SyntaxNode> follow(final SyntaxNode node, final List<String> path) {
    List<SyntaxNode> current = List.of(node);
    for (final String kind : path) {
      current = current.stream().flatMap(parent -> children(parent, kind).stream()).toList();
    }
    return current;
  }
}
