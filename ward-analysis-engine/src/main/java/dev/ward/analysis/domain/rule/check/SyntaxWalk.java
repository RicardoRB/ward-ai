package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read-only navigation over a {@link SyntaxNode} tree, shared by the checks. */
final class SyntaxWalk {

  /** The kind the frontends give a comment node. */
  static final String COMMENT_KIND = "comment";

  private SyntaxWalk() {}

  /**
   * A node with its ancestors, root first.
   *
   * @param node the node
   * @param ancestors the nodes above {@code node}, root first
   */
  record Visit(SyntaxNode node, List<SyntaxNode> ancestors) {}

  /** Returns the nodes of the given kinds in source order, each with its ancestors. */
  static List<Visit> visits(final SyntaxNode root, final Set<String> kinds) {
    final List<Visit> visits = new ArrayList<>();
    collect(root, new ArrayList<>(), kinds, visits);
    return visits;
  }

  private static void collect(
      final SyntaxNode node,
      final List<SyntaxNode> ancestors,
      final Set<String> kinds,
      final List<Visit> visits) {
    if (kinds.contains(node.kind())) {
      visits.add(new Visit(node, List.copyOf(ancestors)));
    }
    ancestors.add(node);
    for (final SyntaxNode child : node.children()) {
      collect(child, ancestors, kinds, visits);
    }
    ancestors.remove(ancestors.size() - 1);
  }

  /** Returns every node below {@code node}, in source order. */
  static List<SyntaxNode> descendants(final SyntaxNode node) {
    final List<SyntaxNode> out = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      out.add(child);
      out.addAll(descendants(child));
    }
    return out;
  }

  /** Returns the token leaves below {@code node} that carry text, in source order. */
  static List<SyntaxNode> leaves(final SyntaxNode node) {
    final List<SyntaxNode> out = new ArrayList<>();
    collectLeaves(node, out);
    return out;
  }

  private static void collectLeaves(final SyntaxNode node, final List<SyntaxNode> out) {
    if (node.children().isEmpty()) {
      if (node.fields().containsKey("text") && !COMMENT_KIND.equals(node.kind())) {
        out.add(node);
      }
      return;
    }
    node.children().forEach(child -> collectLeaves(child, out));
  }

  /** Returns the text of the leaves below {@code node}, joined without separators. */
  static String text(final SyntaxNode node) {
    final StringBuilder out = new StringBuilder();
    leaves(node).forEach(leaf -> out.append(leaf.fields().get("text")));
    return out.toString();
  }

  /**
   * Returns the first node of {@code kind} below {@code node} in source order, not entering a
   * subtree whose kind is in {@code skip}.
   */
  static Optional<SyntaxNode> firstDescendant(
      final SyntaxNode node, final String kind, final Set<String> skip) {
    for (final SyntaxNode child : node.children()) {
      if (kind.equals(child.kind())) {
        return Optional.of(child);
      }
      if (!skip.contains(child.kind())) {
        final Optional<SyntaxNode> found = firstDescendant(child, kind, skip);
        if (found.isPresent()) {
          return found;
        }
      }
    }
    return Optional.empty();
  }
}
