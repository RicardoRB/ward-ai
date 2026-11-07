package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.List;

/**
 * Every declaration of one name, each with the scope that resolves the names inside it.
 *
 * @param parts the declarations
 */
record Sym(List<Located> parts) {

  /** One declaration and the scope it was declared in. */
  record Located(Decl decl, Lookup scope) {}

  Sym {
    parts = List.copyOf(parts);
  }

  /** Returns the declarations that are types. */
  List<Located> types() {
    return parts.stream().filter(located -> located.decl().isType()).toList();
  }

  /** Returns the declarations of {@code kind}. */
  List<Located> of(final Decl.Kind kind) {
    return parts.stream().filter(located -> located.decl().kind() == kind).toList();
  }

  /** Returns this symbol joined with {@code other}. */
  Sym join(final Sym other) {
    if (other == null) {
      return this;
    }
    final List<Located> all = new ArrayList<>(parts);
    all.addAll(other.parts);
    return new Sym(all);
  }

  /** Returns the symbol of {@code decls} declared in {@code scope}, or null when none. */
  static Sym of(final List<Decl> decls, final Lookup scope) {
    return decls.isEmpty()
        ? null
        : new Sym(decls.stream().map(d -> new Located(d, scope)).toList());
  }
}
