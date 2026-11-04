package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.port.DependencyResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves Python imports the way grimp does, which is how Import Linter builds its graph. {@code
 * import a.b} targets module {@code a.b}. {@code from a.b import c} targets {@code a.b.c} when that
 * module exists and {@code a.b} otherwise. A relative import resolves against the importing
 * module's package and produces no edge when it climbs above the top-level package. An import of a
 * module no file provides produces no edge.
 */
public final class PythonDependencyResolver implements DependencyResolver {

  private static final String ABSOLUTE = "abs:";
  private static final String SEPARATOR = ":";
  private static final String DOT = ".";
  private static final String STAR = "*";

  /** Positions of the fields of a {@code from:<level>:<module>:<name>} specifier. */
  private static final int FIELD_LEVEL = 1;

  private static final int FIELD_MODULE = 2;
  private static final int FIELD_NAME = 3;

  private final PythonProjectLayout layout;

  /**
   * Creates the resolver for a project's source roots.
   *
   * @param layout the source roots that map files to modules
   * @throws NullPointerException if the layout is missing
   */
  public PythonDependencyResolver(final PythonProjectLayout layout) {
    this.layout = Objects.requireNonNull(layout, "layout");
  }

  @Override
  public List<DependencyEdge> resolve(final List<FileSummary> summaries) {
    final Map<String, PythonModule> owners = new HashMap<>();
    final Map<String, String> files = new HashMap<>();
    for (final FileSummary summary : summaries) {
      layout
          .moduleOf(summary.path())
          .ifPresent(
              module -> {
                owners.put(summary.path(), module);
                files.merge(module.name(), summary.path(), this::preferred);
              });
    }
    final List<DependencyEdge> edges = new ArrayList<>();
    for (final FileSummary summary : summaries) {
      final Optional<PythonModule> owner = Optional.ofNullable(owners.get(summary.path()));
      for (final Reference reference : summary.imports()) {
        target(reference.specifier(), owner, files)
            .filter(target -> !target.equals(summary.path()))
            .ifPresent(target -> edges.add(new DependencyEdge(summary.path(), target, reference)));
      }
    }
    return edges;
  }

  /**
   * Picks the file for a module that two files both claim, such as {@code x.py} and {@code
   * x/__init__.py}: the package wins, and otherwise the smaller path, so the order of the input
   * does not matter.
   */
  private String preferred(final String existing, final String candidate) {
    final boolean candidatePackage = isPackageFile(candidate);
    if (candidatePackage != isPackageFile(existing)) {
      return candidatePackage ? candidate : existing;
    }
    return candidate.compareTo(existing) < 0 ? candidate : existing;
  }

  private boolean isPackageFile(final String path) {
    return layout.moduleOf(path).map(PythonModule::pkg).orElse(false);
  }

  private static Optional<String> target(
      final String specifier, final Optional<PythonModule> owner, final Map<String, String> files) {
    if (specifier.startsWith(ABSOLUTE)) {
      return fileOf(specifier.substring(ABSOLUTE.length()), files);
    }
    final String[] fields = specifier.split(SEPARATOR, -1);
    final int level = Integer.parseInt(fields[FIELD_LEVEL]);
    return base(level, fields[FIELD_MODULE], owner)
        .flatMap(base -> fromTarget(base, fields[FIELD_NAME], files));
  }

  /** The module a {@code from} import names: the imported submodule if it exists, else the base. */
  private static Optional<String> fromTarget(
      final String base, final String name, final Map<String, String> files) {
    if (STAR.equals(name)) {
      return fileOf(base, files);
    }
    final Optional<String> submodule = fileOf(join(base, name), files);
    return submodule.isPresent() ? submodule : fileOf(base, files);
  }

  /** The absolute module an import at {@code level} with {@code module} refers to. */
  private static Optional<String> base(
      final int level, final String module, final Optional<PythonModule> owner) {
    if (level == 0) {
      return Optional.of(module);
    }
    if (owner.isEmpty()) {
      return Optional.empty();
    }
    final List<String> packageParts = parts(packageOf(owner.get()));
    final int up = level - 1;
    if (up >= packageParts.size()) {
      return Optional.empty();
    }
    final String base = String.join(DOT, packageParts.subList(0, packageParts.size() - up));
    return Optional.of(join(base, module));
  }

  private static String packageOf(final PythonModule module) {
    if (module.pkg()) {
      return module.name();
    }
    final int dot = module.name().lastIndexOf(DOT);
    return dot < 0 ? "" : module.name().substring(0, dot);
  }

  private static Optional<String> fileOf(final String module, final Map<String, String> files) {
    return module.isEmpty() ? Optional.empty() : Optional.ofNullable(files.get(module));
  }

  /** Joins two dotted parts, where either may be empty: {@code from . import x} has no module. */
  private static String join(final String base, final String name) {
    if (name.isEmpty()) {
      return base;
    }
    return base.isEmpty() ? name : base + DOT + name;
  }

  private static List<String> parts(final String dotted) {
    return dotted.isEmpty() ? List.of() : List.of(dotted.split("\\.", -1));
  }
}
