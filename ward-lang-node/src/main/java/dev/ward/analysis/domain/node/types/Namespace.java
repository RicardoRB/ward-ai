package dev.ward.analysis.domain.node.types;

import java.util.List;

/** The scope inside a namespace declaration: its own declarations, then those around it. */
final class Namespace implements Lookup {
  private final Decl decl;
  private final Lookup parent;

  Namespace(final Decl decl, final Lookup parent) {
    this.decl = decl;
    this.parent = parent;
  }

  @Override
  public Sym find(final String name) {
    final Sym own = exported(name);
    if (own != null) {
      return own;
    }
    final List<Decl> local = decl.body().stream().filter(d -> d.name().equals(name)).toList();
    final Sym inner = Sym.of(local, this);
    return inner != null ? inner : parent.find(name);
  }

  @Override
  public Sym exported(final String name) {
    final boolean implicit = decl.body().stream().noneMatch(d -> d.has(Decl.EXPORTED));
    final List<Decl> found =
        decl.body().stream()
            .filter(d -> d.name().equals(name) && (implicit || d.has(Decl.EXPORTED)))
            .toList();
    return Sym.of(found, this);
  }
}
