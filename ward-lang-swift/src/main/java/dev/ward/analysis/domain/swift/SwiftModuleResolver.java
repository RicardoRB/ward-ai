package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.port.DependencyResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Resolves Swift imports to the files of the project modules they name. An import is an edge to
 * every file of the module, because Swift imports a whole module.
 */
public final class SwiftModuleResolver implements DependencyResolver {

  private final SwiftProjectLayout layout;

  /**
   * Creates the resolver for the modules of {@code layout}.
   *
   * @throws NullPointerException if the layout is missing
   */
  public SwiftModuleResolver(final SwiftProjectLayout layout) {
    this.layout = Objects.requireNonNull(layout, "layout");
  }

  @Override
  public List<DependencyEdge> resolve(final List<FileSummary> summaries) {
    final Map<String, List<String>> filesByModule =
        summaries.stream()
            .collect(
                Collectors.groupingBy(
                    summary -> layout.moduleOf(summary.path()),
                    TreeMap::new,
                    Collectors.mapping(FileSummary::path, Collectors.toList())));
    final List<DependencyEdge> edges = new ArrayList<>();
    for (final FileSummary summary : summaries) {
      for (final Reference reference : summary.imports()) {
        addEdges(summary.path(), reference, filesByModule, edges);
      }
    }
    return edges;
  }

  private void addEdges(
      final String from,
      final Reference reference,
      final Map<String, List<String>> filesByModule,
      final List<DependencyEdge> edges) {
    final String module = moduleOf(reference.specifier());
    if (module.isEmpty() || !layout.declares(module)) {
      return;
    }
    for (final String to : filesByModule.getOrDefault(module, List.of())) {
      if (!to.equals(from)) {
        edges.add(new DependencyEdge(from, to, reference));
      }
    }
  }

  /** The module an import specifier names, or an empty string for any other reference. */
  private static String moduleOf(final String specifier) {
    if (!specifier.startsWith(SwiftFileSummarizer.MODULE_PREFIX)) {
      return "";
    }
    return specifier.substring(SwiftFileSummarizer.MODULE_PREFIX.length());
  }
}
