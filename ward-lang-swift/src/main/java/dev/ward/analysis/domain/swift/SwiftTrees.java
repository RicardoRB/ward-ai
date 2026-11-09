package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Navigates the Swift syntax tree for the lint checks: token leaves, compact text and children of a
 * kind. Comments are trivia, so they are never leaves.
 */
final class SwiftTrees {

  private static final String TEXT = "text";
  private static final String COMMENT = "comment";

  private SwiftTrees() {}

  /** Returns the token leaves below {@code node} in source order, comments excluded. */
  static List<SyntaxNode> leaves(final SyntaxNode node) {
    final List<SyntaxNode> out = new ArrayList<>();
    collectLeaves(node, out);
    return out;
  }

  private static void collectLeaves(final SyntaxNode node, final List<SyntaxNode> out) {
    if (node.children().isEmpty()) {
      if (node.fields().containsKey(TEXT) && !COMMENT.equals(node.kind())) {
        out.add(node);
      }
      return;
    }
    node.children().forEach(child -> collectLeaves(child, out));
  }

  /** Returns the leaves' texts of {@code node} joined without separators. */
  static String compact(final SyntaxNode node) {
    final StringBuilder out = new StringBuilder();
    leaves(node).forEach(leaf -> out.append(leaf.fields().get(TEXT)));
    return out.toString();
  }

  /** Returns whether a token leaf below {@code node} has exactly this text. */
  static boolean hasToken(final SyntaxNode node, final String token) {
    return leaves(node).stream().anyMatch(leaf -> token.equals(leaf.fields().get(TEXT)));
  }

  /** Returns the first direct child of {@code kind}, if any. */
  static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).findFirst();
  }

  /** Returns every node of {@code kind} below {@code node}, in source order, not counting it. */
  static List<SyntaxNode> descendants(final SyntaxNode node, final String kind) {
    final List<SyntaxNode> out = new ArrayList<>();
    node.children().forEach(child -> collectKind(child, kind, out));
    return out;
  }

  private static void collectKind(
      final SyntaxNode node, final String kind, final List<SyntaxNode> out) {
    if (kind.equals(node.kind())) {
      out.add(node);
    }
    node.children().forEach(child -> collectKind(child, kind, out));
  }

  /** Returns a match at the start of {@code node}. */
  static RuleMatch at(final SyntaxNode node, final String message) {
    return new RuleMatch(node.span(), message);
  }

  /** Returns a match at the position just after {@code token}; its end column is inclusive. */
  static RuleMatch after(final SyntaxNode token, final String message) {
    final Span end = token.span();
    final int column = end.endColumn() + 1;
    return new RuleMatch(
        new Span(end.endLine(), column, end.endLine(), column, end.endOffset(), end.endOffset()),
        message);
  }
}
