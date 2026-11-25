package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.EnumSet;

/** Handles closures and the accessor clauses of computed properties. */
final class SwiftScopeClosures {
  private static final String IDENTIFIER = "declaration_identifier";

  private final SwiftScopeWalk walk;

  SwiftScopeClosures(final SwiftScopeWalk walk) {
    this.walk = walk;
  }

  void accessor(final SyntaxNode node, final String implicit) {
    walk.scopes.open(ScopeKind.FUNCTION, node.span());
    final SyntaxNode named = SwiftScopeWalk.child(node, "setter_name");
    if (named == null) {
      walk.builder.declare(
          implicit,
          BindingKind.PARAMETER,
          node.span(),
          EnumSet.noneOf(BindingFlag.class)
      );
    } else {
      walk.declare(SwiftScopeWalk.child(named, IDENTIFIER), BindingKind.PARAMETER, false, false);
    }
    node.children().stream().filter(c -> "code_block".equals(c.kind())).forEach(walk::visit);
    walk.scopes.close();
  }

  void closure(final SyntaxNode node) {
    final boolean signature = SwiftScopeWalk.child(node, "closure_signature") != null;
    final int scope = walk.scopes.open(ScopeKind.LAMBDA, node.span());
    if (!signature) {
      walk.builder.markOpen(scope);
    }
    walk.generic(node);
    walk.scopes.close();
  }

  void closureParameter(final SyntaxNode node) {
    walk.declare(node, BindingKind.PARAMETER, false, false);
  }

  void closureIdentifiers(final SyntaxNode node) {
    node.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .forEach(name -> walk.declare(name, BindingKind.PARAMETER, false, false));
  }
}
