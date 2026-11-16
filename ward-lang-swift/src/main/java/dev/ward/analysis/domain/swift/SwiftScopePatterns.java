package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.Span;
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

  SwiftScopePatterns(final SwiftScopeWalk walk) {
    this.walk = walk;
  }

  void patternInitializer(final SyntaxNode node) {
    node.children().stream().filter(c -> INITIALIZER.equals(c.kind())).forEach(walk::visit);
    final SyntaxNode pattern = SwiftScopeWalk.child(node, PATTERN);
    final boolean initialized = SwiftScopeWalk.child(node, INITIALIZER) != null;
    if (pattern != null) {
      shadowRead(node, pattern);
      pattern(pattern, true, initialized);
    }
  }

  /**
   * Records the read of the outer name in {@code let name = name.value} and {@code if let name =
   * name}: the initializer is evaluated before the new binding exists. The scopes resolve a use by
   * its place, and the use stands after the new name, so without this the outer binding looked
   * unused (#567). The extra reference stands right before the new name, in this scope and in the
   * enclosing one.
   */
  private void shadowRead(final SyntaxNode node, final SyntaxNode pattern) {
    final SyntaxNode identifier = SwiftScopeWalk.child(pattern, IDENTIFIER_PATTERN);
    final SyntaxNode name = identifier == null ? null : SwiftScopeWalk.child(identifier, IDENTIFIER);
    final SyntaxNode initializer = SwiftScopeWalk.child(node, INITIALIZER);
    if (name == null || initializer == null || !reads(initializer, SwiftScopeWalk.text(name))) {
      return;
    }
    final Span at = name.span();
    final int offset = Math.max(at.startOffset() - 1, 0);
    final Span before =
        new Span(at.startLine(), at.startColumn(), at.startLine(), at.startColumn(), offset, offset);
    final String text = SwiftScopeWalk.text(name);
    walk.builder.reference(text, before, ReferenceAccess.READ);
    walk.builder.referenceIn(walk.builder.enclosing(), text, before, ReferenceAccess.READ);
  }

  /** Whether the node reads a variable of this name; a member such as {@code .name} is not one. */
  private static boolean reads(final SyntaxNode node, final String name) {
    if ("primary_expression".equals(node.kind()) && node.children().size() == 1) {
      final SyntaxNode only = node.children().get(0);
      if (IDENTIFIER.equals(only.kind())) {
        return name.equals(SwiftScopeWalk.text(only));
      }
    }
    return node.children().stream().anyMatch(child -> reads(child, name));
  }

  void optionalBinding(final SyntaxNode node) {
    node.children().stream().filter(c -> INITIALIZER.equals(c.kind())).forEach(walk::visit);
    if (SwiftScopeWalk.child(node, INITIALIZER) == null) {
      shorthandRead(node);
    } else if (SwiftScopeWalk.child(node, PATTERN) != null) {
      shadowRead(node, SwiftScopeWalk.child(node, PATTERN));
    }
    final BindingKind outer = walk.declaring;
    walk.declaring =
        "var".equals(SwiftScopeWalk.text(node.children().get(0)))
            ? BindingKind.VARIABLE
            : BindingKind.CONSTANT;
    pattern(SwiftScopeWalk.child(node, PATTERN), true, true);
    walk.declaring = outer;
  }

  /**
   * Records the read of {@code if let name} and {@code guard let name}: the shorthand unwraps the
   * outer {@code name}. The reference stands at the keyword, before the new binding, so in this
   * scope it resolves to an earlier binding of the name; the same reference in the enclosing scope
   * reaches a parameter or a variable declared further out.
   */
  private void shorthandRead(final SyntaxNode node) {
    final SyntaxNode pattern = SwiftScopeWalk.child(node, PATTERN);
    final SyntaxNode identifier =
        pattern == null ? null : SwiftScopeWalk.child(pattern, IDENTIFIER_PATTERN);
    final SyntaxNode name = identifier == null ? null : SwiftScopeWalk.child(identifier, IDENTIFIER);
    if (name != null) {
      final String text = SwiftScopeWalk.text(name);
      final Span keyword = node.children().get(0).span();
      walk.builder.reference(text, keyword, ReferenceAccess.READ);
      walk.builder.referenceIn(walk.builder.enclosing(), text, keyword, ReferenceAccess.READ);
    }
  }

  void forIn(final SyntaxNode node) {
    walk.builder.open(ScopeKind.BLOCK, node.span());
    node.children().stream().filter(c -> "expression".equals(c.kind())).forEach(walk::visit);
    final BindingKind outer = walk.declaring;
    walk.declaring = BindingKind.VARIABLE;
    pattern(SwiftScopeWalk.child(node, PATTERN), true, true);
    walk.declaring = outer;
    node.children().stream()
        .filter(c -> !PATTERN.equals(c.kind()) && !"expression".equals(c.kind()))
        .forEach(walk::visit);
    walk.builder.close();
  }

  void catchClause(final SyntaxNode node) {
    walk.builder.open(ScopeKind.BLOCK, node.span());
    if (SwiftScopeWalk.child(node, PATTERN) == null) {
      walk.builder.declare(
          "error",
          BindingKind.PARAMETER,
          node.span(),
          EnumSet.noneOf(BindingFlag.class)
      );
    }
    walk.generic(node);
    walk.builder.close();
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
