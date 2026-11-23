package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.EnumSet;

/** Handles the patterns that bind names: declarations, conditions, loops and cases. */
final class SwiftScopePatterns {
  private static final String IDENTIFIER_PATTERN = "identifier_pattern";

  private static final String IDENTIFIER = "declaration_identifier";
  private static final String PATTERN = "pattern";
  private static final String INITIALIZER = "initializer";

  private final SwiftScopeWalk walk;
  private final SwiftScopeShadows shadows;

  SwiftScopePatterns(final SwiftScopeWalk walk) {
    this.walk = walk;
    this.shadows = new SwiftScopeShadows(walk);
  }

  void patternInitializer(final SyntaxNode node) {
    node.children().stream().filter(c -> INITIALIZER.equals(c.kind())).forEach(walk::visit);
    final SyntaxNode pattern = SwiftScopeWalk.child(node, PATTERN);
    final boolean initialized = SwiftScopeWalk.child(node, INITIALIZER) != null;
    if (pattern != null) {
      shadows.shadowRead(node, pattern);
      pattern(pattern, true, initialized);
    }
  }

  void optionalBinding(final SyntaxNode node) {
    node.children().stream().filter(c -> INITIALIZER.equals(c.kind())).forEach(walk::visit);
    if (SwiftScopeWalk.child(node, INITIALIZER) == null) {
      shadows.shorthandRead(node);
    } else if (SwiftScopeWalk.child(node, PATTERN) != null) {
      shadows.shadowRead(node, SwiftScopeWalk.child(node, PATTERN));
    }
    final BindingKind outer = walk.declaring;
    walk.declaring =
        "var".equals(SwiftScopeWalk.text(node.children().get(0)))
            ? BindingKind.VARIABLE
            : BindingKind.CONSTANT;
    pattern(SwiftScopeWalk.child(node, PATTERN), true, true);
    walk.declaring = outer;
  }

  /** {@code case .some(let x) = x}: the initializer is read before the pattern binds. */
  void caseCondition(final SyntaxNode node) {
    final SyntaxNode initializer = SwiftScopeWalk.child(node, INITIALIZER);
    final SyntaxNode pattern = SwiftScopeWalk.child(node, PATTERN);
    if (initializer == null || pattern == null) {
      walk.generic(node);
      return;
    }
    walk.visit(initializer);
    shadows.caseRead(initializer, pattern);
    walk.visit(pattern);
  }

  void forIn(final SyntaxNode node) {
    walk.scopes.open(ScopeKind.BLOCK, node.span());
    node.children().stream().filter(c -> "expression".equals(c.kind())).forEach(walk::visit);
    final BindingKind outer = walk.declaring;
    walk.declaring = BindingKind.VARIABLE;
    pattern(SwiftScopeWalk.child(node, PATTERN), true, true);
    walk.declaring = outer;
    node.children().stream()
        .filter(c -> !PATTERN.equals(c.kind()) && !"expression".equals(c.kind()))
        .forEach(walk::visit);
    walk.scopes.close();
  }

  void catchClause(final SyntaxNode node) {
    walk.scopes.open(ScopeKind.BLOCK, node.span());
    if (SwiftScopeWalk.child(node, PATTERN) == null) {
      walk.builder.declare(
          "error",
          BindingKind.PARAMETER,
          node.span(),
          EnumSet.noneOf(BindingFlag.class)
      );
    }
    walk.generic(node);
    walk.scopes.close();
  }

  /** Walks a pattern: names are declared when {@code binding}, and read otherwise. */
  void pattern(final SyntaxNode node, final boolean binding, final boolean written) {
    switch (node.kind()) {
      case IDENTIFIER_PATTERN -> identifierPattern(node, binding, written, false);
      case "value_binding_pattern" -> valueBinding(node);
      case "tuple_pattern" -> node.children().forEach(child -> tuple(child, binding, written));
      default -> node.children().forEach(child -> patternPart(child, binding, written));
    }
  }

  void tuple(final SyntaxNode node, final boolean binding, final boolean written) {
    if (IDENTIFIER_PATTERN.equals(node.kind())) {
      identifierPattern(node, binding, written, true);
    } else if (PATTERN.equals(node.kind()) || node.kind().startsWith("tuple_pattern")) {
      node.children().forEach(child -> tuple(child, binding, written));
    } else {
      pattern(node, binding, written);
    }
  }

  void patternPart(final SyntaxNode node, final boolean binding, final boolean written) {
    if (PATTERN.equals(node.kind()) || node.kind().endsWith("_pattern")) {
      pattern(node, binding, written);
    } else {
      walk.visit(node);
    }
  }

  void valueBinding(final SyntaxNode node) {
    final BindingKind outer = walk.declaring;
    walk.declaring =
        "var".equals(SwiftScopeWalk.text(node.children().get(0)))
            ? BindingKind.VARIABLE
            : BindingKind.CONSTANT;
    node.children().stream()
        .filter(child -> PATTERN.equals(child.kind()))
        .forEach(child -> pattern(child, true, true));
    walk.declaring = outer;
  }

  void identifierPattern(
      final SyntaxNode node,
      final boolean binding,
      final boolean written,
      final boolean tuple
  ) {
    final SyntaxNode name = SwiftScopeWalk.child(node, IDENTIFIER);
    if (name == null) {
      return;
    }
    if (binding) {
      walk.declare(name, walk.declaring, written, tuple);
    } else {
      walk.builder.reference(SwiftScopeWalk.text(name), name.span(), ReferenceAccess.READ);
    }
  }
}
