package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.FactKind;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Picks the answer for a span from the nodes that have it, for the kinds that were asked. */
final class FactPick {
  private FactPick() {}

  static <T> T asked(final Set<FactKind> kinds, final FactKind kind, final Supplier<T> fact) {
    return kinds.contains(kind) ? fact.get() : null;
  }

  static <T> List<T> list(
      final Set<FactKind> kinds, final FactKind kind, final Supplier<List<T>> facts) {
    return kinds.contains(kind) ? facts.get() : List.of();
  }

  static <T> T first(final List<SyntaxNode> nodes, final Function<SyntaxNode, T> fact) {
    for (final SyntaxNode node : nodes) {
      final T found = fact.apply(node);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  static <T> List<T> firstList(
      final List<SyntaxNode> nodes, final Function<SyntaxNode, List<T>> facts) {
    for (final SyntaxNode node : nodes) {
      final List<T> found = facts.apply(node);
      if (!found.isEmpty()) {
        return found;
      }
    }
    return List.of();
  }
}
