package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The module references of one file, found by one walk over its syntax tree. */
final class RustImports {
  private static final String MODULE = "module";
  private static final String USE_TREE = "useTree";
  private static final String SIMPLE_PATH = "simplePath";
  private static final String SEGMENT = "simplePathSegment";
  private static final String SEMICOLON = "';'";
  private static final String OPEN = "'{'";
  private static final String STAR = "'*'";
  private static final String SELF = "self";
  private static final String SUPER = "super";
  private static final String SEPARATOR = "::";

  private final List<Reference> references = new ArrayList<>();
  private final List<String> inline = new ArrayList<>();
  private final Set<String> children = new HashSet<>();

  RustImports(final SyntaxNode tree) {
    collectChildren(tree);
    walk(tree);
  }

  List<Reference> references() {
    return List.copyOf(references);
  }

  /** The names of the modules the file declares at its top level, which a path may start with. */
  private void collectChildren(final SyntaxNode node) {
    if (MODULE.equals(node.kind())) {
      children.add(moduleName(node));
      return;
    }
    node.children().forEach(this::collectChildren);
  }

  private void walk(final SyntaxNode node) {
    if (MODULE.equals(node.kind())) {
      module(node);
    } else if (RustKinds.USE_DECLARATION.equals(node.kind())) {
      useTree(RustTrees.child(node, USE_TREE), List.of());
    } else {
      node.children().forEach(this::walk);
    }
  }

  private void module(final SyntaxNode node) {
    final String name = moduleName(node);
    if (hasToken(node, SEMICOLON)) {
      emit(List.of(SELF, name), node.span());
      return;
    }
    inline.add(name);
    node.children().forEach(this::walk);
    inline.remove(inline.size() - 1);
  }

  private static String moduleName(final SyntaxNode node) {
    return RustTrees.name(RustTrees.child(node, RustKinds.IDENTIFIER));
  }

  private void useTree(final SyntaxNode tree, final List<String> prefix) {
    if (tree == null) {
      return;
    }
    final SyntaxNode path = RustTrees.child(tree, SIMPLE_PATH);
    final List<String> segments = new ArrayList<>(prefix);
    if (path != null) {
      RustTrees.children(path, SEGMENT).forEach(part -> segments.add(RustTrees.text(part)));
    }
    if (hasToken(tree, OPEN)) {
      RustTrees.children(tree, USE_TREE).forEach(child -> useTree(child, segments));
    } else if (hasToken(tree, STAR) || path != null) {
      emit(withoutSelf(segments), path == null ? tree.span() : path.span());
    }
  }

  private static List<String> withoutSelf(final List<String> segments) {
    final int last = segments.size() - 1;
    return last > 0 && SELF.equals(segments.get(last)) ? segments.subList(0, last) : segments;
  }

  private static boolean hasToken(final SyntaxNode tree, final String token) {
    return tree.children().stream().anyMatch(child -> RustTrees.isToken(child, token));
  }

  private void emit(final List<String> segments, final Span span) {
    if (!segments.isEmpty()) {
      references.add(new Reference(String.join(SEPARATOR, rewritten(segments)), span));
    }
  }

  /** Rewrites a path written inside inline modules into a path from the file's own module. */
  private List<String> rewritten(final List<String> segments) {
    final String first = segments.getFirst();
    if (SELF.equals(first)) {
      return join(List.of(SELF), inline, segments.subList(1, segments.size()));
    }
    if (SUPER.equals(first)) {
      return superPath(segments);
    }
    if (inline.isEmpty() && children.contains(first)) {
      return join(List.of(SELF), List.of(), segments);
    }
    return segments;
  }

  private List<String> superPath(final List<String> segments) {
    int ups = 0;
    while (ups < segments.size() && SUPER.equals(segments.get(ups))) {
      ups++;
    }
    final List<String> rest = segments.subList(ups, segments.size());
    if (ups <= inline.size()) {
      return join(List.of(SELF), inline.subList(0, inline.size() - ups), rest);
    }
    return join(java.util.Collections.nCopies(ups - inline.size(), SUPER), List.of(), rest);
  }

  private static List<String> join(
      final List<String> head,
      final List<String> middle,
      final List<String> tail
  ) {
    final List<String> all = new ArrayList<>(head);
    all.addAll(middle);
    all.addAll(tail);
    return all;
  }
}
