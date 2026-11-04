package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Symbol;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the references of one Kotlin file by Kotlin name lookup: an aliased or explicit import,
 * then a declaration of the same file, then the same package, then the star imports. A member call
 * such as {@code total.toMoney()} resolves only to a lower-case declaration, the convention of
 * functions and properties, of its package or of a star import. Kotlin's default imports name no
 * project declaration, so they give no edge.
 */
final class KotlinFileResolution {
  private static final String IMPORT = "import";
  private static final String NAME = "name";
  private static final String MEMBER = "member";
  private static final String ON_DEMAND = "on-demand";
  private static final String PACKAGE = "package";
  private static final String ALIAS = "alias";

  private final JvmSourceIndex index;
  private final FileSummary summary;
  private final Set<String> declaredInFile = new HashSet<>();
  private final List<String> starImports = new ArrayList<>();
  private final Map<String, String> aliases = new HashMap<>();
  private final Map<String, String> singleImports = new HashMap<>();
  private String packageName = "";

  KotlinFileResolution(final JvmSourceIndex index, final FileSummary summary) {
    this.index = index;
    this.summary = summary;
    for (final Symbol declared : summary.declared()) {
      declaredInFile.add(declared.name());
    }
    readHeader();
  }

  /** Returns the edges from this file, one per reference that resolves to another file. */
  List<DependencyEdge> edges() {
    final List<DependencyEdge> edges = new ArrayList<>();
    for (final Reference reference : summary.imports()) {
      for (final String target : targets(reference)) {
        if (!target.equals(summary.path())) {
          edges.add(new DependencyEdge(summary.path(), target, reference));
        }
      }
    }
    return edges;
  }

  /** Reads the package, the aliases, the star imports and the explicit imports of the file. */
  private void readHeader() {
    final Set<String> aliased = new HashSet<>();
    for (final Reference reference : summary.imports()) {
      final String value = JavaNames.valueOf(reference.specifier());
      if (reference.specifier().startsWith(PACKAGE + ":")) {
        packageName = value;
      } else if (reference.specifier().startsWith(ALIAS + ":")) {
        final int equals = value.indexOf('=');
        aliases.put(value.substring(0, equals), value.substring(equals + 1));
        aliased.add(value.substring(equals + 1));
      } else if (reference.specifier().startsWith(ON_DEMAND + ":")) {
        starImports.add(value);
      }
    }
    for (final Reference reference : summary.imports()) {
      final String value = JavaNames.valueOf(reference.specifier());
      if (reference.specifier().startsWith(IMPORT + ":") && !aliased.contains(value)) {
        singleImports.put(JavaNames.simpleName(value), value);
      }
    }
  }

  private Set<String> targets(final Reference reference) {
    final String specifier = reference.specifier();
    final String value = JavaNames.valueOf(specifier);
    if (specifier.startsWith(IMPORT + ":")) {
      return visible(value);
    }
    if (specifier.startsWith(NAME + ":")) {
      return name(value);
    }
    if (specifier.startsWith(MEMBER + ":")) {
      return member(value);
    }
    return Set.of();
  }

  /** A qualified name resolves by its longest declared prefix, else by its first segment. */
  private Set<String> name(final String value) {
    final String[] segments = value.split("\\.");
    for (int count = segments.length; count >= 2; count--) {
      final Set<String> files = visible(JavaNames.prefix(segments, count));
      if (!files.isEmpty()) {
        return files;
      }
    }
    return lookupSimple(segments[0]);
  }

  private Set<String> lookupSimple(final String name) {
    if (aliases.containsKey(name)) {
      return visible(aliases.get(name));
    }
    if (singleImports.containsKey(name)) {
      return visible(singleImports.get(name));
    }
    if (declaredInFile.contains(JavaNames.qualify(packageName, name))) {
      return Set.of();
    }
    final Set<String> samePackage = visible(JavaNames.qualify(packageName, name));
    if (!samePackage.isEmpty()) {
      return samePackage;
    }
    final Set<String> onDemand = new HashSet<>();
    for (final String imported : starImports) {
      onDemand.addAll(visible(JavaNames.qualify(imported, name)));
    }
    return onDemand;
  }

  /**
   * A member call names a function or property of the package or of a star import. Declarations are
   * not told apart by kind in the summary, so a capitalized name, which is a class, is ignored.
   */
  private Set<String> member(final String name) {
    if (!Character.isLowerCase(name.charAt(0))
        || declaredInFile.contains(JavaNames.qualify(packageName, name))) {
      return Set.of();
    }
    final Set<String> files = new HashSet<>(visible(JavaNames.qualify(packageName, name)));
    for (final String imported : starImports) {
      files.addAll(visible(JavaNames.qualify(imported, name)));
    }
    return files;
  }

  private Set<String> visible(final String type) {
    return index.visibleDeclarations(summary.path(), type);
  }
}
