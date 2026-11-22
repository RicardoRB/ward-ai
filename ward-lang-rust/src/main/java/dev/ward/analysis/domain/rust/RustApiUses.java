package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Expands the {@code use} declarations of a Rust module into one path and name per import. */
final class RustApiUses {
  private static final String TREE = "useTree";
  private static final String PATH = "simplePath";
  private static final String SEGMENT = "simplePathSegment";
  private static final String OPEN = "'{'";
  private static final String AS = "'as'";
  private static final String SELF = "self";

  /** A path imported under a name. */
  record Imported(List<String> path, String alias) {
    Imported {
      path = List.copyOf(path);
    }
  }

  private RustApiUses() {}

  /** Returns the named imports of a {@code useDeclaration}; glob imports are left out. */
  static List<Imported> of(final SyntaxNode declaration) {
    final List<Imported> found = new ArrayList<>();
    final SyntaxNode tree = RustTrees.child(declaration, TREE);
    if (tree != null) {
      expand(tree, List.of(), found);
    }
    return found;
  }

  private static void expand(
      final SyntaxNode tree,
      final List<String> prefix,
      final List<Imported> found
  ) {
    final List<String> path = new ArrayList<>(prefix);
    path.addAll(segments(RustTrees.child(tree, PATH)));
    if (RustTrees.leaf(tree, OPEN) != null && RustTrees.children(tree, TREE).size() > 0) {
      RustTrees.children(tree, TREE).forEach(inner -> expand(inner, path, found));
    } else if (!path.isEmpty()) {
      found.add(imported(tree, path));
    }
  }

  private static Imported imported(final SyntaxNode tree, final List<String> path) {
    final List<String> target =
        SELF.equals(path.getLast()) && path.size() > 1 ? path.subList(0, path.size() - 1) : path;
    final SyntaxNode alias = RustTrees.child(tree, RustKinds.IDENTIFIER);
    return new Imported(
        target,
        RustTrees.leaf(tree, AS) != null && alias != null
            ? RustTrees.name(alias)
            : target.getLast()
    );
  }

  private static List<String> segments(final SyntaxNode path) {
    return path == null
        ? List.of()
        : RustTrees.children(path, SEGMENT).stream().map(RustTrees::text).toList();
  }
}
