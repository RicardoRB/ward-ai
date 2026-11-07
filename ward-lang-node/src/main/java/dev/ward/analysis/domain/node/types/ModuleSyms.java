package dev.ward.analysis.domain.node.types;

import java.util.List;

/** Helpers split out of {@link Module}. */
final class ModuleSyms {
  private static final String DEFAULT = "default";
  private static final String STAR = "*";

  private ModuleSyms() {}

  static Decl namespaceOf(final String specifier, final Module target) {
    return new Decl(
        Decl.Kind.NAMESPACE,
        specifier,
        Decl.Header.NONE,
        List.of(),
        TsType.UNKNOWN,
        target.decls(),
        Decl.MODULE);
  }

  static Sym starExport(final FileDecls.Export export, final Module target, final String name) {
    return STAR.equals(export.exported()) && !DEFAULT.equals(name) ? target.exported(name) : null;
  }

  static Sym namespaceSym(final String specifier, final Module target) {
    return new Sym(List.of(new Sym.Located(namespaceOf(specifier, target), target)));
  }

  static Sym join(final Sym first, final Sym second) {
    if (first == null) {
      return second;
    }
    return second == null ? first : first.join(second);
  }
}
