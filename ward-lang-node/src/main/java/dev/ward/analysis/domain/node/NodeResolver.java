package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.port.DependencyResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves the module references of TypeScript and JavaScript files to project files, the way Node
 * and TypeScript do without a type checker. A reference that names nothing in the project, such as
 * an external package or a built-in module, yields no edge.
 */
public final class NodeResolver implements DependencyResolver {
  private final NodeProjectLayout layout;
  private final NodeSpecifiers specifiers;

  /** Creates the resolver over the project described by {@code layout}. */
  public NodeResolver(final NodeProjectLayout layout) {
    this.layout = layout;
    this.specifiers = new NodeSpecifiers(layout);
  }

  /**
   * The files that each workspace package's main, module, types and exports targets name. A target
   * that is not an analysed file, such as a built output, declares no entry.
   */
  @Override
  public Set<String> entryFiles() {
    return layout.packages().stream()
        .flatMap(
            pkg ->
                Stream.concat(
                        Stream.of(pkg.main(), pkg.module(), pkg.types()),
                        pkg.exports().values().stream()
                            .flatMap(List::stream)
                            .map(NodeProjectLayout.ExportEntry::target))
                    .filter(Objects::nonNull)
                    .map(target -> NodePaths.join(pkg.directory(), target))
                    .flatMap(Optional::stream)
                    .filter(layout.files()::contains))
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public List<DependencyEdge> resolve(final List<FileSummary> summaries) {
    final List<DependencyEdge> edges = new ArrayList<>();
    for (final FileSummary summary : summaries) {
      for (final Reference reference : summary.imports()) {
        resolveOne(summary.path(), reference)
            .ifPresent(to -> edges.add(new DependencyEdge(summary.path(), to, reference)));
      }
    }
    return List.copyOf(edges);
  }

  private Optional<String> resolveOne(final String from, final Reference reference) {
    return NodeReference.parse(reference.specifier())
        .flatMap(parsed -> specifiers.resolve(from, parsed));
  }
}
