package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.List;

/**
 * The scopes of the branching expressions: an {@code if} or {@code while} with its {@code let}
 * conditions, a {@code for} loop, the arms of a {@code match} and a closure.
 */
final class RustScopeBranches {
  /** The arm's pattern, the arrow and the expression that follows it. */
  private static final int ARM_SPAN = 2;

  private final RustScopeWalk walk;

  RustScopeBranches(final RustScopeWalk walk) {
    this.walk = walk;
  }

  /**
   * An {@code if} or {@code while}: the {@code let} conditions bind in the rest of the condition
   * and the body, never in the {@code else} branch.
   */
  void conditional(final SyntaxNode node) {
    final int depth = walk.scopes.depth();
    final List<SyntaxNode> children = node.children();
    final int split = elseAt(children);
    final SyntaxNode body = RustTrees.child(node, RustKinds.BLOCK);
    walk.scopes.pushReach(body == null ? node.span() : body.span());
    children.subList(0, split).forEach(walk::visit);
    walk.scopes.popReach();
    walk.scopes.closeTo(depth);
    children.subList(split, children.size()).forEach(walk::visit);
  }

  private static int elseAt(final List<SyntaxNode> children) {
    for (int index = 0; index < children.size(); index++) {
      if (RustTrees.isToken(children.get(index), RustKinds.ELSE)) {
        return index;
      }
    }
    return children.size();
  }

  /** A {@code for} loop: the iterable is read before the pattern binds. */
  void forLoop(final SyntaxNode node) {
    final int depth = walk.scopes.depth();
    final SyntaxNode iterable = RustTrees.child(node, RustKinds.EXPRESSION);
    if (iterable != null) {
      walk.visit(iterable);
    }
    walk.scopes.open(ScopeKind.BLOCK, node.span());
    walk.patterns.bind(RustTrees.child(node, RustKinds.PATTERN), BindingKind.CONSTANT, true, false);
    walk.visit(RustTrees.child(node, RustKinds.BLOCK));
    walk.scopes.closeTo(depth);
  }

  /** The arms of a {@code match}: each arm is a scope that holds its pattern's names. */
  void matchArms(final SyntaxNode node) {
    final int depth = walk.scopes.depth();
    final List<SyntaxNode> children = node.children();
    for (int index = 0; index < children.size(); index++) {
      final SyntaxNode child = children.get(index);
      if (RustKinds.MATCH_ARM.equals(child.kind())) {
        walk.scopes.closeTo(depth);
        final int last = Math.min(index + ARM_SPAN, children.size() - 1);
        walk.scopes.open(ScopeKind.BLOCK, RustTrees.join(child.span(), children.get(last).span()));
        arm(child);
      } else {
        walk.visit(child);
      }
    }
    walk.scopes.closeTo(depth);
  }

  private void arm(final SyntaxNode node) {
    final SyntaxNode pattern = RustTrees.child(node, RustKinds.PATTERN);
    walk.patterns.bind(pattern, BindingKind.CONSTANT, true, false);
    node.children().stream().filter(child -> child != pattern).forEach(walk::visit);
  }

  /** A closure: a scope of its own that holds its parameters. */
  void closure(final SyntaxNode node) {
    final int depth = walk.scopes.depth();
    walk.scopes.open(ScopeKind.LAMBDA, node.span());
    walk.generic(node);
    walk.scopes.closeTo(depth);
  }
}
