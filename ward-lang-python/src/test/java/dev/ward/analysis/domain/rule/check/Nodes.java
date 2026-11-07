package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builders of hand-made syntax nodes for the tree-based check tests. */
final class Nodes {

  private static final String TEXT = "text";

  private Nodes() {}

  /** A token with the given text. */
  static SyntaxNode leaf(final String text) {
    return new SyntaxNode("TOKEN", at(0, text.length()), Map.of(TEXT, text), List.of());
  }

  /** A node of the given kind over the given children. */
  static SyntaxNode node(final String kind, final SyntaxNode... children) {
    return new SyntaxNode(kind, at(0, 1), Map.of(), List.of(children));
  }

  static SyntaxNode block(final SyntaxNode... children) {
    return node("block", children);
  }

  static SyntaxNode statement(final SyntaxNode child) {
    return node("stmt", child);
  }

  /** A {@code try} with a body and the given handlers, in source order. */
  static SyntaxNode tryWith(final SyntaxNode... handlers) {
    final List<SyntaxNode> children = new ArrayList<>(List.of(leaf("try"), block(leaf("pass"))));
    children.addAll(List.of(handlers));
    return node("try_stmt", children.toArray(SyntaxNode[]::new));
  }

  /** An {@code except} clause with the given tokens after the keyword. */
  static SyntaxNode clause(final String... parts) {
    final List<SyntaxNode> children = new ArrayList<>(List.of(leaf("except")));
    for (final String part : parts) {
      children.add(leaf(part));
    }
    return node("except_clause", children.toArray(SyntaxNode[]::new));
  }

  /** A {@code raise} statement made of the given tokens. */
  static SyntaxNode raise(final String... parts) {
    final List<SyntaxNode> children = new ArrayList<>();
    for (final String part : parts) {
      children.add(leaf(part));
    }
    return node("raise_stmt", children.toArray(SyntaxNode[]::new));
  }

  /** A comparison {@code v <operator> <right>}. */
  static SyntaxNode comparison(final String operator, final String right) {
    return node("comparison", leaf("v"), node("comp_op", leaf(operator)), leaf(right));
  }

  /** The span of a token at the given offset. */
  static Span at(final int offset, final int length) {
    return new Span(1, offset + 1, 1, offset + length + 1, offset, offset + length);
  }
}
