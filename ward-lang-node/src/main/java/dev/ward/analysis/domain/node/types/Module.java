package dev.ward.analysis.domain.node.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The scope of one file, of one ambient module declared in a file, or of global declarations. */
final class Module implements Lookup {

  /** What a module scope stands for. */
  enum Kind {
    /** A source or declaration file. */
    FILE,
    /** A module declared with {@code declare module "name"}. */
    AMBIENT,
    /** Declarations that are visible everywhere. */
    GLOBAL
  }

  private static final String DEFAULT = "default";
  private static final String STAR = "*";
  private static final String ASSIGNMENT = "=";

  private final Program program;
  private final String path;
  private final FileDecls file;
  private final List<Decl> decls;
  private final Kind kind;
  private final Map<String, Sym> declared = new HashMap<>();
  private final Set<String> resolving = new HashSet<>();

  Module(
      final Program program,
      final String path,
      final FileDecls file,
      final List<Decl> decls,
      final Kind kind) {
    this.program = program;
    this.path = path;
    this.file = file;
    this.decls = List.copyOf(decls);
    this.kind = kind;
    final Map<String, List<Decl>> byName = new HashMap<>();
    for (final Decl decl : this.decls) {
      byName.computeIfAbsent(decl.name(), key -> new ArrayList<>()).add(decl);
    }
    byName.forEach((name, list) -> declared.put(name, Sym.of(list, this)));
  }

  String path() {
    return path;
  }

  FileDecls file() {
    return file;
  }

  @Override
  public Sym find(final String name) {
    if (kind == Kind.GLOBAL) {
      return program.globals().find(name);
    }
    final Sym own = declared.get(name);
    final Sym imported = imported(name);
    final Sym local = own == null ? imported : own.join(imported);
    return local != null ? local : program.globals().find(name);
  }

  /** Returns the declarations of {@code name} made in this scope, without imports or globals. */
  Sym own(final String name) {
    return declared.get(name);
  }

  /** Returns the names this scope declares. */
  Set<String> names() {
    return declared.keySet();
  }

  private Sym imported(final String name) {
    final FileDecls.Import origin = file.imports().get(name);
    if (origin == null || !resolving.add(name)) {
      return null;
    }
    try {
      final Module target = program.moduleFor(path, origin.specifier());
      if (target == null) {
        return null;
      }
      return STAR.equals(origin.member())
          ? new Sym(List.of(new Sym.Located(namespaceOf(origin.specifier(), target), target)))
          : target.exported(origin.member());
    } finally {
      resolving.remove(name);
    }
  }

  private static Decl namespaceOf(final String specifier, final Module target) {
    return new Decl(
        Decl.Kind.NAMESPACE,
        specifier,
        Decl.Header.NONE,
        List.of(),
        Ty.UNKNOWN,
        target.decls,
        Decl.MODULE);
  }

  @Override
  public Sym exported(final String name) {
    if (!resolving.add("export:" + name)) {
      return null;
    }
    try {
      return exportedOnce(name);
    } finally {
      resolving.remove("export:" + name);
    }
  }

  private Sym exportedOnce(final String name) {
    Sym found = explicit(name);
    for (final FileDecls.Export export : file.exports()) {
      found = join(found, fromClause(export, name));
    }
    return found;
  }

  private Sym explicit(final String name) {
    final boolean implicit =
        (kind != Kind.FILE || path.endsWith(".d.ts"))
            && file.exports().isEmpty()
            && decls.stream().noneMatch(decl -> decl.has(Decl.EXPORTED));
    final List<Decl> found = new ArrayList<>();
    for (final Decl decl : decls) {
      final boolean isDefault = decl.has(Decl.DEFAULT) && DEFAULT.equals(name);
      if ((decl.name().equals(name) || isDefault) && (implicit || decl.has(Decl.EXPORTED))) {
        found.add(decl);
      }
    }
    return Sym.of(found, this);
  }

  private Sym fromClause(final FileDecls.Export export, final String name) {
    if (ASSIGNMENT.equals(export.exported())) {
      return assignment(export.local(), name);
    }
    if (export.specifier().isEmpty()) {
      return export.exported().equals(name) ? declaredOrImported(export.local()) : null;
    }
    final Module target = program.moduleFor(path, export.specifier());
    if (target == null) {
      return null;
    }
    if (STAR.equals(export.local())) {
      return export.exported().equals(name)
          ? namespaceSym(export.specifier(), target)
          : starExport(export, target, name);
    }
    return export.exported().equals(name) ? target.exported(export.local()) : null;
  }

  private Sym starExport(final FileDecls.Export export, final Module target, final String name) {
    return STAR.equals(export.exported()) && !DEFAULT.equals(name) ? target.exported(name) : null;
  }

  private static Sym namespaceSym(final String specifier, final Module target) {
    return new Sym(List.of(new Sym.Located(namespaceOf(specifier, target), target)));
  }

  private Sym declaredOrImported(final String local) {
    return find(local);
  }

  /** {@code export = x}: the module is {@code x}, whose namespace members are the exports. */
  private Sym assignment(final String local, final String name) {
    final Sym target = find(local);
    if (target == null) {
      return null;
    }
    if (DEFAULT.equals(name)) {
      return target;
    }
    return Names.member(target, name);
  }

  private static Sym join(final Sym first, final Sym second) {
    if (first == null) {
      return second;
    }
    return second == null ? first : first.join(second);
  }
}
