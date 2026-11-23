package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Binds the names a pattern introduces. A name an or-pattern repeats in each alternative is bound
 * once. An identifier pattern that starts with an upper-case letter and has no {@code mut}, {@code
 * ref} or {@code @} is a constant or a variant, so it is read, not bound.
 */
final class RustScopePatterns {
  private static final Set<String> DESTRUCTURING =
      Set.of("tuplePattern", "structPattern", "tupleStructPattern", "slicePattern");
  private static final String REF = "'ref'";

  private final RustScopeWalk walk;

  RustScopePatterns(final RustScopeWalk walk) {
    this.walk = walk;
  }

  /** What a pattern binds, and how. */
  private record Binder(BindingKind kind, boolean written, boolean gated, Set<String> seen) {}

  /** A function parameter: binds its pattern as parameters, then walks the type. */
  void parameter(final SyntaxNode node) {
    for (final SyntaxNode child : node.children()) {
      if (RustKinds.PATTERN.equals(child.kind()) || RustKinds.PATTERN_NO_ALT.equals(child.kind())) {
        bind(child, BindingKind.PARAMETER, false, false);
      } else {
        walk.visit(child);
      }
    }
  }

  /** A closure parameter: binds its pattern as a parameter, then walks the type. */
  void closureParameter(final SyntaxNode node) {
    parameter(node);
  }

  /**
   * Binds the names of {@code pattern} in the current scope.
   *
   * @param pattern a node of kind {@code pattern} or {@code patternNoTopAlt}
   * @param kind what to bind a plain name as, unless it is {@code mut}
   * @param written whether the matched value is assigned to the names right away
   */
  /**
   * Binds the names of {@code pattern}; a {@code gated} binding sits under a {@code cfg} attribute
   * or an {@code allow} of an unused lint, so it is not judged.
   */
  void bind(
      final SyntaxNode pattern,
      final BindingKind kind,
      final boolean written,
      final boolean gated
  ) {
    node(pattern, new Binder(kind, written, gated, new HashSet<>()), false);
  }

  private void node(final SyntaxNode node, final Binder binder, final boolean nested) {
    switch (node.kind()) {
      case RustKinds.IDENTIFIER_PATTERN -> identifier(node, binder, nested);
      case "structPatternField" -> field(node, binder);
      case "pathInExpression", "macroInvocation", "qualifiedPathInExpression" -> walk.visit(node);
      default -> {
        final boolean inside = nested || DESTRUCTURING.contains(node.kind());
        node.children().forEach(child -> node(child, binder, inside));
      }
    }
  }

  private void identifier(final SyntaxNode node, final Binder binder, final boolean nested) {
    final SyntaxNode name = RustTrees.child(node, RustKinds.IDENTIFIER);
    final boolean mutable = hasToken(node, RustKinds.MUT);
    final SyntaxNode sub = RustTrees.child(node, RustKinds.PATTERN);
    if (isConstantName(node, name, sub)) {
      walk.builder.reference(RustTrees.name(name), name.span(), ReferenceAccess.READ);
      return;
    }
    declare(name, mutable, nested, binder);
    if (sub != null) {
      node(sub, binder, true);
    }
  }

  private static boolean isConstantName(
      final SyntaxNode node,
      final SyntaxNode name,
      final SyntaxNode sub
  ) {
    final String text = RustTrees.name(name);
    return sub == null
        && !hasToken(node, RustKinds.MUT)
        && !hasToken(node, REF)
        && Character.isUpperCase(text.codePointAt(0));
  }

  private void field(final SyntaxNode node, final Binder binder) {
    if (hasToken(node, RustKinds.COLON)) {
      final SyntaxNode pattern = RustTrees.child(node, RustKinds.PATTERN);
      node(pattern, binder, true);
      return;
    }
    final SyntaxNode name = RustTrees.child(node, RustKinds.IDENTIFIER);
    if (name != null) {
      declare(name, hasToken(node, RustKinds.MUT), true, binder);
    }
  }

  private void declare(
      final SyntaxNode name,
      final boolean mutable,
      final boolean nested,
      final Binder binder
  ) {
    final String text = RustTrees.name(name);
    if (!binder.seen().add(text)) {
      return;
    }
    final Set<BindingFlag> flags = EnumSet.noneOf(BindingFlag.class);
    if (nested) {
      flags.add(BindingFlag.DESTRUCTURED);
    }
    if (binder.gated()) {
      flags.add(BindingFlag.EXPORTED);
    }
    walk.builder.declare(text, kindOf(binder.kind(), mutable), name.span(), flags);
    if (binder.written()) {
      walk.builder.reference(text, name.span(), ReferenceAccess.WRITE);
    }
  }

  private static BindingKind kindOf(final BindingKind kind, final boolean mutable) {
    if (kind == BindingKind.PARAMETER) {
      return kind;
    }
    return mutable ? BindingKind.VARIABLE : BindingKind.CONSTANT;
  }

  private static boolean hasToken(final SyntaxNode node, final String kind) {
    return node.children().stream().anyMatch(child -> RustTrees.isToken(child, kind));
  }
}
