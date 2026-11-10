package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Finds the method or constructor a node sits in, from its ancestors. */
final class Callables {

  private static final Set<String> KINDS = Set.of("methodDeclaration", "constructorDeclaration");

  private Callables() {}

  /** Returns the innermost method or constructor among {@code ancestors}, if any. */
  static Optional<SyntaxNode> nearest(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (KINDS.contains(ancestors.get(index).kind())) {
        return Optional.of(ancestors.get(index));
      }
    }
    return Optional.empty();
  }
}
