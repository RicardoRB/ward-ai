package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.Set;

/**
 * The scopes of items: the root of a file, an inline module, and the generic parameters and {@code
 * self} that an item's own scope holds. What an item names is declared by {@link
 * RustScopeDeclarations}.
 */
final class RustScopeItems {
  private static final String TYPE_PARAM = "typeParam";
  private static final String CONST_PARAM = "constParam";

  private final RustScopeWalk walk;

  RustScopeItems(final RustScopeWalk walk) {
    this.walk = walk;
  }

  /** The root of a file: declares its items, then walks them. */
  void crate(final SyntaxNode node) {
    walk.declarations.declareAll(RustTrees.children(node, RustKinds.ITEM));
    walk.generic(node);
  }

  /** An inline {@code mod}: a scope with its own items. */
  void inlineModule(final SyntaxNode node) {
    final int depth = walk.scopes.depth();
    walk.scopes.open(ScopeKind.BLOCK, node.span());
    walk.declarations.declareAll(RustTrees.children(node, RustKinds.ITEM));
    walk.generic(node);
    walk.scopes.closeTo(depth);
  }

  /**
   * An item with a scope of its own. It is a function-kind scope even for a type or a constant, so
   * that a use of a later item from its signature is not a use before declaration.
   */
  void scoped(final SyntaxNode node) {
    final int depth = walk.scopes.depth();
    walk.scopes.open(ScopeKind.FUNCTION, node.span());
    walk.generic(node);
    walk.scopes.closeTo(depth);
  }

  /** Declares the type and const parameters, then walks their bounds and defaults. */
  void genericParams(final SyntaxNode node) {
    for (final SyntaxNode parameter : RustTrees.children(node, "genericParam")) {
      parameter.children().forEach(this::genericParam);
    }
  }

  private void genericParam(final SyntaxNode node) {
    if (!Set.of(TYPE_PARAM, CONST_PARAM).contains(node.kind())) {
      return;
    }
    final SyntaxNode name = RustTrees.child(node, RustKinds.IDENTIFIER);
    if (name != null) {
      final BindingKind kind =
          CONST_PARAM.equals(node.kind()) ? BindingKind.CONSTANT : BindingKind.TYPE;
      walk.builder.declare(RustTrees.name(name), kind, name.span(), Set.of());
    }
    node.children().stream().filter(child -> child != name).forEach(walk::visit);
  }

  /** Declares {@code self} for a method with a receiver and walks a typed receiver's type. */
  void selfParam(final SyntaxNode node) {
    final SyntaxNode self = RustTrees.leaf(node, RustKinds.SELF);
    if (self != null) {
      walk.builder.declare("self", BindingKind.PARAMETER, self.span(), Set.of());
    }
    walk.generic(node);
  }
}
