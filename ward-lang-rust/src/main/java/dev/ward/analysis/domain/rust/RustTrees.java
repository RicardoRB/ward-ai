package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads over the syntax tree of a Rust file that the scope analysis and the lowering share. */
final class RustTrees {
  /** The prefix of a raw identifier such as {@code r#type}. */
  private static final String RAW_PREFIX = "r#";

  private static final String TEXT = "text";

  private RustTrees() {}

  /** The direct children of {@code node} with {@code kind}, in source order. */
  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }

  /** The first direct child of {@code node} with {@code kind}, or null. */
  static SyntaxNode child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).findFirst().orElse(
        null
    );
  }

  /** The first node of {@code kind} at or below {@code node}, or null. */
  static SyntaxNode find(final SyntaxNode node, final String kind) {
    if (kind.equals(node.kind())) {
      return node;
    }
    for (final SyntaxNode child : node.children()) {
      final SyntaxNode found = find(child, kind);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /** The first leaf of {@code kind} at or below {@code node}, or null. */
  static SyntaxNode leaf(final SyntaxNode node, final String kind) {
    if (isToken(node, kind)) {
      return node;
    }
    for (final SyntaxNode child : node.children()) {
      final SyntaxNode found = leaf(child, kind);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /** The source text of the leaves under {@code node}, joined. */
  static String text(final SyntaxNode node) {
    final StringBuilder out = new StringBuilder();
    append(node, out);
    return out.toString();
  }

  private static void append(final SyntaxNode node, final StringBuilder out) {
    if (node.children().isEmpty()) {
      out.append(node.fields().getOrDefault(TEXT, ""));
    }
    node.children().forEach(child -> append(child, out));
  }

  /** The name an {@code identifier} node spells, without the {@code r#} of a raw identifier. */
  static String name(final SyntaxNode identifier) {
    final String text = text(identifier);
    return text.startsWith(RAW_PREFIX) ? text.substring(RAW_PREFIX.length()) : text;
  }

  /** The region from the start of {@code from} to the end of {@code to}, which does not precede it. */
  static Span join(final Span from, final Span to) {
    return new Span(
        from.startLine(),
        from.startColumn(),
        to.endLine(),
        to.endColumn(),
        from.startOffset(),
        to.endOffset()
    );
  }

  /** Whether {@code node} is a leaf of {@code kind}, such as {@code '='}. */
  /**
   * Whether {@code node} has a {@code #[cfg(..)]} attribute, so that another configuration may be
   * the one that uses it, or an {@code allow} of an unused or dead-code lint.
   */
  static boolean silencesUnused(final SyntaxNode node) {
    return children(node, "outerAttribute").stream()
        .map(RustTrees::text)
        .anyMatch(
            text ->
                text.startsWith("#[cfg(")
                    || (text.startsWith("#[allow(") || text.startsWith("#[expect("))
                    && (text.contains("unused") || text.contains("dead_code"))
        );
  }

  static boolean isToken(final SyntaxNode node, final String kind) {
    return node.children().isEmpty() && kind.equals(node.kind());
  }
}
