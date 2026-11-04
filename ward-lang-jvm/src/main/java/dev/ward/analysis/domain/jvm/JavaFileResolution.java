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
 * Resolves the references of one Java file. A simple type name is looked up in the order of the JLS
 * 6.4.1: a single-type import, a type declared in the file, a type of the same package, then the
 * on-demand imports, of which every match counts.
 */
final class JavaFileResolution {
  private static final String IMPORT = "import";
  private static final String STATIC = "static";
  private static final String NAME = "name";

  private final JvmSourceIndex index;
  private final FileSummary summary;
  private String packageName = "";
  private final Map<String, String> singleTypeImports = new HashMap<>();
  private final List<String> onDemandImports = new ArrayList<>();
  private final Set<String> declaredSimpleNames = new HashSet<>();

  JavaFileResolution(final JvmSourceIndex index, final FileSummary summary) {
    this.index = index;
    this.summary = summary;
    readImports();
    for (final Symbol type : summary.declared()) {
      declaredSimpleNames.add(JavaNames.simpleName(type.name()));
    }
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

  private void readImports() {
    for (final Reference reference : summary.imports()) {
      final String specifier = reference.specifier();
      final String value = JavaNames.valueOf(specifier);
      if (specifier.startsWith("package:")) {
        packageName = value;
      } else if (specifier.startsWith(IMPORT + ":")) {
        singleTypeImports.put(JavaNames.simpleName(value), value);
      } else if (specifier.startsWith("on-demand:")) {
        onDemandImports.add(value);
      }
    }
  }

  private Set<String> targets(final Reference reference) {
    final String specifier = reference.specifier();
    final String value = JavaNames.valueOf(specifier);
    if (specifier.startsWith(IMPORT + ":")) {
      return visible(value);
    }
    if (specifier.startsWith(STATIC + ":")) {
      return staticMember(value);
    }
    if (specifier.startsWith(NAME + ":")) {
      return name(value);
    }
    return Set.of();
  }

  /** A static import names a member or a type; the longest declared prefix is the type. */
  private Set<String> staticMember(final String value) {
    final String[] segments = value.split("\\.");
    for (int count = segments.length; count >= 1; count--) {
      final Set<String> files = visible(JavaNames.prefix(segments, count));
      if (!files.isEmpty()) {
        return files;
      }
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
    if (singleTypeImports.containsKey(name)) {
      return visible(singleTypeImports.get(name));
    }
    if (declaredSimpleNames.contains(name)) {
      return Set.of();
    }
    final Set<String> samePackage = visible(JavaNames.qualify(packageName, name));
    if (!samePackage.isEmpty()) {
      return samePackage;
    }
    final Set<String> onDemand = new HashSet<>();
    for (final String imported : onDemandImports) {
      onDemand.addAll(visible(JavaNames.qualify(imported, name)));
    }
    return onDemand;
  }

  private Set<String> visible(final String type) {
    return index.visibleDeclarations(summary.path(), type);
  }
}
