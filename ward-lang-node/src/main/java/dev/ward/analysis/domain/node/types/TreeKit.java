package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads the parse trees of TypeScript and JavaScript. Comment and error nodes are skipped. */
final class TreeKit {
  private static final String COMMENT = "comment";
  private static final String ERROR = "error";
  private static final String TEXT = "text";

  private TreeKit() {}

  /** Returns the children that are grammar parts, in source order. */
  static List<SyntaxNode> parts(final SyntaxNode node) {
    final List<SyntaxNode> parts = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (!COMMENT.equals(child.kind()) && !ERROR.equals(child.kind())) {
        parts.add(child);
      }
    }
    return parts;
  }

  /** Returns the first part of {@code kind}, or null. */
  static SyntaxNode child(final SyntaxNode node, final String kind) {
    for (final SyntaxNode part : parts(node)) {
      if (kind.equals(part.kind())) {
        return part;
      }
    }
    return null;
  }

  /** Returns the parts of {@code kind}. */
  static List<SyntaxNode> children(final SyntaxNode node, final String kind) {
    return parts(node).stream().filter(part -> kind.equals(part.kind())).toList();
  }

  /** Returns the first descendant of {@code kind}, searching depth first, or null. */
  static SyntaxNode find(final SyntaxNode node, final String kind) {
    for (final SyntaxNode part : parts(node)) {
      if (kind.equals(part.kind())) {
        return part;
      }
      final SyntaxNode deeper = find(part, kind);
      if (deeper != null) {
        return deeper;
      }
    }
    return null;
  }

  /** Returns every descendant of {@code kind}, not descending into the ones found. */
  static List<SyntaxNode> findAll(final SyntaxNode node, final String kind) {
    final List<SyntaxNode> found = new ArrayList<>();
    for (final SyntaxNode part : parts(node)) {
      if (kind.equals(part.kind())) {
        found.add(part);
      } else {
        found.addAll(findAll(part, kind));
      }
    }
    return found;
  }

  /** Returns whether a direct part is the token {@code token}. */
  static boolean hasToken(final SyntaxNode node, final String token) {
    for (final SyntaxNode part : parts(node)) {
      if (part.children().isEmpty() && token.equals(part.fields().get(TEXT))) {
        return true;
      }
      if (part.children().size() == 1
          && part.children().get(0).children().isEmpty()
          && token.equals(part.children().get(0).fields().get(TEXT))
          && !"identifier".equals(part.kind())) {
        return true;
      }
    }
    return false;
  }
}
