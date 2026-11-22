package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import java.util.List;

/**
 * The names that paths read. Only the first segment of a path is a name in scope; the rest is
 * looked up inside what the first names. A path that starts with {@code ::} names a crate.
 */
final class RustScopePaths {
  private static final String SEPARATOR = "'::'";

  private final RustScopeWalk walk;

  RustScopePaths(final RustScopeWalk walk) {
    this.walk = walk;
  }

  /** A path in an expression or a pattern: reads its first segment, walks its generic arguments. */
  void expressionPath(final SyntaxNode node) {
    path(node, RustKinds.PATH_EXPR_SEGMENT);
  }

  /** A path in a type: reads its first segment, walks its generic arguments and fn inputs. */
  void typePath(final SyntaxNode node) {
    path(node, "typePathSegment");
  }

  private void path(final SyntaxNode node, final String segmentKind) {
    final List<SyntaxNode> segments = RustTrees.children(node, segmentKind);
    final boolean rooted =
        !node.children().isEmpty() && RustTrees.isToken(node.children().get(0), SEPARATOR);
    if (!rooted && !segments.isEmpty()) {
      read(RustTrees.child(segments.get(0), RustKinds.PATH_IDENT_SEGMENT));
    }
    segments.forEach(this::arguments);
  }

  private void arguments(final SyntaxNode segment) {
    segment.children().stream()
        .filter(child -> !RustKinds.PATH_IDENT_SEGMENT.equals(child.kind()))
        .filter(child -> !SEPARATOR.equals(child.kind()))
        .forEach(walk::visit);
  }

  private void read(final SyntaxNode segment) {
    if (segment == null) {
      return;
    }
    final SyntaxNode name = RustTrees.child(segment, RustKinds.IDENTIFIER);
    final SyntaxNode self = RustTrees.child(segment, RustKinds.SELF);
    if (name != null) {
      walk.builder.reference(RustTrees.name(name), name.span(), ReferenceAccess.READ);
    } else if (self != null) {
      walk.builder.reference("self", self.span(), ReferenceAccess.READ);
    }
  }

  /** The binding of an associated type, {@code Item = T}: only the type is read. */
  void binding(final SyntaxNode node) {
    node.children().stream()
        .filter(child -> !RustKinds.IDENTIFIER.equals(child.kind()))
        .forEach(walk::visit);
  }
}
