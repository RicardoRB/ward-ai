package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FileSummary;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The types each project file declares, keyed by qualified name, and which files may see which. A
 * file outside every source root is a main source of its own module.
 */
final class JvmSourceIndex {
  private static final JavaSourceRoot UNROOTED = new JavaSourceRoot("", "", false);

  private final Map<String, Set<String>> declaringFiles = new HashMap<>();
  private final List<JavaSourceRoot> roots;

  /** Indexes the declarations of {@code summaries} under the source {@code roots}. */
  JvmSourceIndex(final List<FileSummary> summaries, final List<JavaSourceRoot> roots) {
    this.roots =
        roots.stream()
            .sorted(
                Comparator.comparingInt((JavaSourceRoot root) -> root.directory().length())
                    .reversed())
            .toList();
    summaries.stream()
        .flatMap(
            summary ->
                summary.declared().stream().map(type -> Map.entry(type.name(), summary.path())))
        .forEach(
            entry ->
                declaringFiles
                    .computeIfAbsent(entry.getKey(), name -> new LinkedHashSet<>())
                    .add(entry.getValue()));
  }

  /** Returns the files visible to {@code from} that declare the qualified name {@code type}. */
  Set<String> visibleDeclarations(final String from, final String type) {
    final Set<String> files = new LinkedHashSet<>();
    for (final String to : declaringFiles.getOrDefault(type, Set.of())) {
      if (visible(from, to)) {
        files.add(to);
      }
    }
    return files;
  }

  /** Main sources are visible everywhere; test sources only to test sources of their module. */
  private boolean visible(final String from, final String to) {
    final JavaSourceRoot target = rootOf(to);
    if (!target.test()) {
      return true;
    }
    final JavaSourceRoot source = rootOf(from);
    return source.test() && source.module().equals(target.module());
  }

  private JavaSourceRoot rootOf(final String path) {
    return roots.stream().filter(root -> root.contains(path)).findFirst().orElse(UNROOTED);
  }
}
