package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.ImportOrigin;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The names a {@code use} tree brings in. {@code use a::{b, c as d, self}} declares {@code b},
 * {@code d} and {@code a}; a glob makes the scope open; {@code as _} brings in no name.
 */
final class RustScopeImports {
  private static final String SIMPLE_PATH = "simplePath";
  private static final String SEGMENT = "simplePathSegment";
  private static final String PATH_SEPARATOR = "::";

  private final RustScopeWalk walk;

  RustScopeImports(final RustScopeWalk walk) {
    this.walk = walk;
  }

  /** Declares the names of {@code tree} below the path {@code prefix}. */
  void useTree(final SyntaxNode tree, final List<String> prefix, final Set<BindingFlag> flags) {
    if (tree == null) {
      return;
    }
    final SyntaxNode path = RustTrees.child(tree, SIMPLE_PATH);
    final List<String> segments = join(prefix, path);
    if (has(tree, "'{'")) {
      RustTrees.children(tree, "useTree").forEach(child -> useTree(child, segments, flags));
    } else if (has(tree, "'*'")) {
      walk.builder.markOpen(walk.builder.current());
    } else if (path != null) {
      importedName(tree, path, segments, flags);
    }
  }

  private static boolean has(final SyntaxNode tree, final String token) {
    return tree.children().stream().anyMatch(child -> RustTrees.isToken(child, token));
  }

  private static List<String> join(final List<String> prefix, final SyntaxNode path) {
    final List<String> segments = new ArrayList<>(prefix);
    if (path != null) {
      RustTrees.children(path, SEGMENT).forEach(segment -> segments.add(RustTrees.text(segment)));
    }
    return segments;
  }

  private void importedName(
      final SyntaxNode tree,
      final SyntaxNode path,
      final List<String> segments,
      final Set<BindingFlag> flags
  ) {
    if (has(tree, "'_'")) {
      return;
    }
    final SyntaxNode alias = RustTrees.child(tree, RustKinds.IDENTIFIER);
    final List<SyntaxNode> own = RustTrees.children(path, SEGMENT);
    final SyntaxNode last = own.get(own.size() - 1);
    final boolean self = "self".equals(RustTrees.text(last));
    final String member = self ? "" : RustTrees.text(last);
    final List<String> from = segments.subList(0, segments.size() - 1);
    final String name = nameOf(alias, self ? from.get(from.size() - 1) : member);
    final Span at = alias == null ? last.span() : alias.span();
    walk.builder.declareImport(
        name,
        at,
        new ImportOrigin(String.join(PATH_SEPARATOR, from), member, path.span()),
        flags
    );
  }

  private static String nameOf(final SyntaxNode alias, final String plain) {
    return alias == null ? plain : RustTrees.name(alias);
  }
}
