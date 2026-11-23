package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.List;
import java.util.Objects;

/**
 * The scopes of blocks and {@code let} statements. A {@code let} starts a scope that lasts to the
 * end of its block, so the value of a {@code let} is read before its names exist. {@code
 * let}-{@code else} reads its value and runs its {@code else} block before the names exist too.
 */
final class RustScopeFlow {
  private final RustScopeWalk walk;

  RustScopeFlow(final RustScopeWalk walk) {
    this.walk = walk;
  }

  /** A block: its items are visible from its start, its {@code let}s open scopes inside it. */
  void block(final SyntaxNode node) {
    final int depth = walk.scopes.depth();
    walk.scopes.open(ScopeKind.BLOCK, node.span());
    walk.scopes.pushReach(node.span());
    walk.declarations.declareAll(items(node));
    walk.generic(node);
    walk.scopes.popReach();
    walk.scopes.closeTo(depth);
  }

  private static List<SyntaxNode> items(final SyntaxNode block) {
    final SyntaxNode statements = RustTrees.child(block, RustKinds.STATEMENTS);
    if (statements == null) {
      return List.of();
    }
    return RustTrees.children(statements, RustKinds.STATEMENT).stream()
        .map(statement -> RustTrees.child(statement, RustKinds.ITEM))
        .filter(Objects::nonNull)
        .toList();
  }

  /** A {@code let} statement: the names exist after the statement, not in its own value. */
  void letStatement(final SyntaxNode node) {
    final SyntaxNode pattern = RustTrees.child(node, RustKinds.PATTERN_NO_ALT);
    node.children().stream().filter(child -> child != pattern).forEach(walk::visit);
    if (pattern != null) {
      final boolean initialized =
          node.children().stream().anyMatch(child -> RustTrees.isToken(child, RustKinds.EQUALS));
      walk.scopes.openAfter(node.span());
      walk.patterns.bind(
          pattern,
          BindingKind.CONSTANT,
          initialized,
          RustTrees.silencesUnused(node)
      );
    }
  }
}
