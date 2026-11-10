package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.port.DependencyResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Resolves Swift imports to the files of the project modules they name. An import is an edge to
 * every file of the module, because Swift imports a whole module. A name use is an edge to each
 * other file of the same module that declares a type with that name.
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

  /** The module of {@code path}, or empty when the layout places it in the implicit module. */
  @Override
  public Optional<String> moduleOf(final String path) {
    final String module = layout.moduleOf(path);
    return SwiftProjectLayout.IMPLICIT_MODULE.equals(module)
        ? Optional.empty()
        : Optional.of(module);
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
    final Map<String, Map<String, List<String>>> typesByModule = typesByModule(summaries);
    final List<DependencyEdge> edges = new ArrayList<>();
    for (final FileSummary summary : summaries) {
      for (final Reference reference : summary.imports()) {
        if (reference.specifier().startsWith(SwiftFileSummarizer.NAME_PREFIX)) {
          addNameEdges(summary.path(), reference, typesByModule, edges);
        } else {
          addEdges(summary.path(), reference, filesByModule, edges);
        }
      }
    }
    return edges;
  }

  /** Indexes the files that declare each type name, by the module that owns them. */
  private Map<String, Map<String, List<String>>> typesByModule(final List<FileSummary> summaries) {
    final Map<String, Map<String, List<String>>> index = new TreeMap<>();
    for (final FileSummary summary : summaries) {
      addDeclarations(index, layout.moduleOf(summary.path()), summary);
    }
    return index;
  }

  private static void addDeclarations(
      final Map<String, Map<String, List<String>>> index,
      final String module,
      final FileSummary summary) {
    final Map<String, List<String>> types = index.computeIfAbsent(module, key -> new TreeMap<>());
    summary
        .declared()
        .forEach(
            symbol ->
                types
                    .computeIfAbsent(
                        SwiftFileSummarizer.lastComponent(symbol.name()), name -> new ArrayList<>())
                    .add(summary.path()));
  }

  private void addNameEdges(
      final String from,
      final Reference reference,
      final Map<String, Map<String, List<String>>> typesByModule,
      final List<DependencyEdge> edges) {
    final String name = reference.specifier().substring(SwiftFileSummarizer.NAME_PREFIX.length());
    final Map<String, List<String>> types =
        typesByModule.getOrDefault(layout.moduleOf(from), Map.of());
    for (final String to : types.getOrDefault(name, List.of())) {
      if (!to.equals(from)) {
        edges.add(new DependencyEdge(from, to, reference));
      }
    }
  }

  private void addEdges(
      final String from,
      final Reference reference,
      final Map<String, List<String>> filesByModule,
      final List<DependencyEdge> edges) {
    final String module = importedModule(reference.specifier());
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
  private static String importedModule(final String specifier) {
    if (!specifier.startsWith(SwiftFileSummarizer.MODULE_PREFIX)) {
      return "";
    }
    return specifier.substring(SwiftFileSummarizer.MODULE_PREFIX.length());
  }
}
