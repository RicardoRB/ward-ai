package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Reads a syntax tree by node kind. Tokens are the leaves that carry a {@code text} field. */
final class SyntaxTrees {
  private static final String TEXT = "text";

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

  /** Returns the first direct child of {@code node} with {@code kind}. */
  static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> child.kind().equals(kind)).findFirst();
  }

  /** Returns the direct children of {@code node} with {@code kind}. */
  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> child.kind().equals(kind)).toList();
  }

  /** Returns the declared name held by a child of {@code kind}, or empty when there is none. */
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
