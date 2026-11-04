package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.rule.DependencyView;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The graph of one run and the summaries it was built from, by path.
 *
 * @param graph the resolved dependencies
 * @param summaries the summary of each summarized file, by project-root-relative path
 */
public record DependencySnapshot(DependencyGraph graph, Map<String, FileSummary> summaries) {

  /** The snapshot of a run with no dependency adapters: no summaries and no edges. */
  public static final DependencySnapshot EMPTY =
      new DependencySnapshot(new DependencyGraph(List.of()), Map.of());

  /**
   * Copies the summaries.
   *
   * @throws NullPointerException if a field is missing
   */
  public DependencySnapshot {
    Objects.requireNonNull(graph, "graph");
    summaries = Map.copyOf(summaries);
  }

  /** Returns the view of the file at {@code path}: its direct dependencies and dependents. */
  public DependencyView view(final String path) {
    return new DependencyView(
        summariesOf(graph.dependenciesOf(path)), summariesOf(graph.dependentsOf(path)));
  }

  private List<FileSummary> summariesOf(final List<String> paths) {
    return paths.stream().map(summaries::get).filter(Objects::nonNull).toList();
  }
}
