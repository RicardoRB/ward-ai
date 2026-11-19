package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** Reads the shapes of the C# syntax tree that the scope analysis and the lowering share. */
final class CSharpSyntax {
  /** The kind of the rule that names a declaration or a use. */
  static final String IDENTIFIER = "identifier";

  private static final int SOLE_CHILD = 1;

  private CSharpSyntax() {}

  /** Returns the text of the tokens under {@code node}, joined without a separator. */
  static String text(final SyntaxNode node) {
    final StringBuilder out = new StringBuilder();
    append(node, out);
    return out.toString();
  }

  private static void append(final SyntaxNode node, final StringBuilder out) {
    if (node.children().isEmpty()) {
      out.append(node.fields().getOrDefault("text", ""));
    }
    node.children().forEach(child -> append(child, out));
  }

  /** Returns the first child of {@code kind}, or empty. */
  static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).findFirst();
  }

  /** Returns the children of {@code kind}, in source order. */
  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).toList();
  }

  /** Tells whether {@code node} has a token child spelled {@code token}. */
  static boolean hasToken(final SyntaxNode node, final String token) {
    return node.children().stream()
        .anyMatch(child -> child.children().isEmpty() && token.equals(child.fields().get("text")));
  }

  /** Returns the direct {@code identifier} child that names a declaration, or empty. */
  static Optional<SyntaxNode> name(final SyntaxNode node) {
    return child(node, IDENTIFIER);
  }

  /** Returns the last identifier of a dotted member name such as {@code IFoo.Bar}. */
  static Optional<SyntaxNode> lastName(final SyntaxNode memberName) {
    final List<SyntaxNode> names = children(memberName, IDENTIFIER);
    return names.isEmpty() ? Optional.empty() : Optional.of(names.get(names.size() - 1));
  }

  /** Follows single-child chains from {@code node} down to a node of {@code kind}, or empty. */
  static Optional<SyntaxNode> soleDescendant(final SyntaxNode node, final String kind) {
    SyntaxNode current = node;
    while (!kind.equals(current.kind())) {
      if (current.children().size() != SOLE_CHILD) {
        return Optional.empty();
      }
      current = current.children().get(0);
    }
    return Optional.of(current);
  }
}
