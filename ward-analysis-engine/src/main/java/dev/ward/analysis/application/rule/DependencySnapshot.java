package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.rule.DependencyView;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The graph of one run and the summaries it was built from, by path.
 *
 * @param graph the resolved dependencies
 * @param summaries the summary of each summarized file, by project-root-relative path
 * @param entries the entry files that the manifests of the project declare
 */
public record DependencySnapshot(
    DependencyGraph graph, Map<String, FileSummary> summaries, Set<String> entries) {

  /** The snapshot of a run with no dependency adapters: no summaries and no edges. */
  public static final DependencySnapshot EMPTY =
      new DependencySnapshot(new DependencyGraph(List.of()), Map.of(), Set.of());

  /**
   * Creates a snapshot with no entry files.
   *
   * @throws NullPointerException if a field is missing
   */
  public DependencySnapshot(final DependencyGraph graph, final Map<String, FileSummary> summaries) {
    this(graph, summaries, Set.of());
  }

  /**
   * Copies the summaries and entries.
   *
   * @throws NullPointerException if a field is missing
   */
  public DependencySnapshot {
    Objects.requireNonNull(graph, "graph");
    summaries = Map.copyOf(summaries);
    entries = Set.copyOf(entries);
  }

  /** Returns the view of the file at {@code path}: its direct dependencies and dependents. */
  public DependencyView view(final String path) {
    return new DependencyView(
        summariesOf(graph.dependenciesOf(path)),
        summariesOf(graph.dependentsOf(path)),
        graph.outgoingEdgesOf(path));
  }

  private List<FileSummary> summariesOf(final List<String> paths) {
    return paths.stream().map(summaries::get).filter(Objects::nonNull).toList();
  }
}
