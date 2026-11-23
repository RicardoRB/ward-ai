package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import java.util.ArrayList;
import java.util.List;

/**
 * Records the reads of an outer name by a binding that takes the same name: {@code if let name},
 * {@code guard let name} and {@code let name = name.value}.
 */
final class SwiftScopeShadows {
  private static final String IDENTIFIER_PATTERN = "identifier_pattern";
  private static final String IDENTIFIER = "declaration_identifier";
  private static final String PATTERN = "pattern";
  private static final String INITIALIZER = "initializer";

  private final SwiftScopeWalk walk;

  SwiftScopeShadows(final SwiftScopeWalk walk) {
    this.walk = walk;
  }

  /**
   * Records the read of the outer name in {@code let name = name.value} and {@code if let name =
   * name}: the initializer is evaluated before the new binding exists. The scopes resolve a use by
   * its place, and the use stands after the new name, so without this the outer binding looked
   * unused (#567). The extra reference stands right before the new name, in this scope and in the
   * enclosing one.
   */
  void shadowRead(final SyntaxNode node, final SyntaxNode pattern) {
    final SyntaxNode identifier = SwiftScopeWalk.child(pattern, IDENTIFIER_PATTERN);
    final SyntaxNode name = identifier == null ? null : SwiftScopeWalk.child(
        identifier,
        IDENTIFIER
    );
    final SyntaxNode initializer = SwiftScopeWalk.child(node, INITIALIZER);
    if (name == null || initializer == null || !reads(initializer, SwiftScopeWalk.text(name))) {
      return;
    }
    readBefore(name);
  }

  /**
   * Records the read of the outer name in {@code guard case .some(let name) = name}: a case
   * pattern binds new names after its initializer is evaluated, so the initializer reads the outer
   * one (#753).
   */
  void caseRead(final SyntaxNode initializer, final SyntaxNode pattern) {
    final List<SyntaxNode> names = new ArrayList<>();
    boundNames(pattern, names);
    names.stream()
        .filter(name -> reads(initializer, SwiftScopeWalk.text(name)))
        .forEach(this::readBefore);
  }

  private static void boundNames(final SyntaxNode node, final List<SyntaxNode> into) {
    if (IDENTIFIER_PATTERN.equals(node.kind())) {
      final SyntaxNode name = SwiftScopeWalk.child(node, IDENTIFIER);
      if (name != null) {
        into.add(name);
      }
      return;
    }
    node.children().forEach(child -> boundNames(child, into));
  }

  private void readBefore(final SyntaxNode name) {
    final Span at = name.span();
    final int offset = Math.max(at.startOffset() - 1, 0);
    final Span before =
        new Span(
            at.startLine(),
            at.startColumn(),
            at.startLine(),
            at.startColumn(),
            offset,
            offset
        );
    final String text = SwiftScopeWalk.text(name);
    walk.builder.reference(text, before, ReferenceAccess.READ);
    walk.builder.referenceIn(walk.scopes.enclosing(), text, before, ReferenceAccess.READ);
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

  /**
   * Records the read of {@code if let name} and {@code guard let name}: the shorthand unwraps the
   * outer {@code name}. The reference stands at the keyword, before the new binding, so in this
   * scope it resolves to an earlier binding of the name; the same reference in the enclosing scope
   * reaches a parameter or a variable declared further out.
   */
  void shorthandRead(final SyntaxNode node) {
    final SyntaxNode pattern = SwiftScopeWalk.child(node, PATTERN);
    final SyntaxNode identifier =
        pattern == null ? null : SwiftScopeWalk.child(pattern, IDENTIFIER_PATTERN);
    final SyntaxNode name = identifier == null ? null : SwiftScopeWalk.child(
        identifier,
        IDENTIFIER
    );
    if (name != null) {
      final String text = SwiftScopeWalk.text(name);
      final Span keyword = node.children().get(0).span();
      walk.builder.reference(text, keyword, ReferenceAccess.READ);
      walk.builder.referenceIn(walk.scopes.enclosing(), text, keyword, ReferenceAccess.READ);
    }
  }
}
