package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repairs {@code new Name(args).member(...)}. The grammar reads it as {@code new
 * (Name(args).member) (...)}, so the construction is buried in the callee; this puts the {@code
 * new} back on {@code Name(args)} and applies the member accesses and calls after it.
 */
final class NewExpressions {
  private static final String ARGUMENTS = "arguments";
  private static final int NEW_WITH_ARGUMENTS = 3;

  private NewExpressions() {}

  /** Returns the repaired expression, or empty when {@code node} is not a misread {@code new}. */
  static Optional<SyntaxNode> repair(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    if (parts.size() < 2 || !"new".equals(Exprs.token(parts.get(0)))) {
      return Optional.empty();
    }
    final boolean hasArguments = parts.size() == NEW_WITH_ARGUMENTS;
    if (hasArguments && !ARGUMENTS.equals(parts.get(2).kind())) {
      return Optional.empty();
    }
    final List<List<SyntaxNode>> suffixes = new ArrayList<>();
    SyntaxNode current = parts.get(1);
    while (!isCall(current)) {
      final List<SyntaxNode> inner = TreeKit.parts(current);
      if (inner.size() < 2 || !Exprs.SINGLE.equals(inner.get(0).kind())) {
        return Optional.empty();
      }
      suffixes.add(inner.subList(1, inner.size()));
      current = inner.get(0);
    }
    return Optional.of(rebuild(node, parts, current, suffixes, hasArguments));
  }

  private static boolean isCall(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    return parts.size() == 2
        && ARGUMENTS.equals(parts.get(1).kind())
        && Exprs.nameOf(parts.get(0)) != null;
  }

  private static SyntaxNode rebuild(
      final SyntaxNode node,
      final List<SyntaxNode> parts,
      final SyntaxNode call,
      final List<List<SyntaxNode>> suffixes,
      final boolean hasArguments) {
    final List<SyntaxNode> inner = TreeKit.parts(call);
    SyntaxNode built =
        wrap(
            ChainBuilder.cover(parts.get(0).span(), call.span()),
            List.of(parts.get(0), inner.get(0), inner.get(1)));
    for (int index = suffixes.size() - 1; index >= 0; index--) {
      final List<SyntaxNode> suffix = suffixes.get(index);
      final List<SyntaxNode> children = new ArrayList<>();
      children.add(built);
      children.addAll(suffix);
      built =
          wrap(ChainBuilder.cover(built.span(), suffix.get(suffix.size() - 1).span()), children);
    }
    if (hasArguments) {
      return wrap(node.span(), List.of(built, parts.get(2)));
    }
    return wrap(node.span(), built.children());
  }

  private static SyntaxNode wrap(final Span span, final List<SyntaxNode> children) {
    return new SyntaxNode(Exprs.SINGLE, span, Map.of(), children);
  }
}
