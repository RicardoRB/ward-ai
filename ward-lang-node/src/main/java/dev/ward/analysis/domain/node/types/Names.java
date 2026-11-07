package dev.ward.analysis.domain.node.types;

/** Resolves dotted names through scopes and namespaces. */
final class Names {
  private Names() {}

  /**
   * Returns the declarations of {@code name}, which may be qualified, as seen from {@code scope}.
   */
  static Sym resolve(final Lookup scope, final String name) {
    final int dot = name.indexOf('.');
    if (dot < 0) {
      return scope.find(name);
    }
    Sym current = scope.find(name.substring(0, dot));
    String rest = name.substring(dot + 1);
    while (current != null) {
      final int next = rest.indexOf('.');
      final String part = next < 0 ? rest : rest.substring(0, next);
      current = member(current, part);
      if (next < 0) {
        return current;
      }
      rest = rest.substring(next + 1);
    }
    return null;
  }

  /** Returns the declarations of {@code name} exported by the namespaces in {@code sym}. */
  static Sym member(final Sym sym, final String name) {
    return sym.parts().stream()
        .filter(located -> located.decl().kind() == Decl.Kind.NAMESPACE)
        .map(located -> exportedBy(located, name))
        .filter(inner -> inner != null)
        .reduce((first, second) -> second.join(first))
        .orElse(null);
  }

  private static Sym exportedBy(final Sym.Located located, final String name) {
    if (located.decl().has(Decl.MODULE) && located.scope() instanceof Module module) {
      return module.exported(name);
    }
    return new Namespace(located.decl(), located.scope()).exported(name);
  }
}
