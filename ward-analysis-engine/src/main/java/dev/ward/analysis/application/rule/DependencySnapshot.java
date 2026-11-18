package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.DependencyView;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.TreeSet;
import java.util.Set;

/**
 * The graph of one run and the summaries it was built from, by path.
 *
 * @param graph the resolved dependencies
 * @param summaries the summary of each summarized file, by project-root-relative path
 * @param entries the entry files that the manifests of the project declare
 * @param units every source unit of the run, by path, for a rule that reads the files around its
 *     unit
 */
public record DependencySnapshot(
    DependencyGraph graph,
    Map<String,
    FileSummary> summaries,
    Set<String> entries,
    Map<String,
    SourceUnit> units,
    Map<String,
    Set<String>> subtypes
) {
  /** The snapshot of a run with no dependency adapters: no summaries and no edges. */
  public static final DependencySnapshot EMPTY =
      new DependencySnapshot(new DependencyGraph(List.of()), Map.of(), Set.of(), Map.of());

  /** Creates a snapshot of the units, with the files that extend or implement each type. */
  public DependencySnapshot(
      final DependencyGraph graph,
      final Map<String,
      FileSummary> summaries,
      final Set<String> entries,
      final Map<String,
      SourceUnit> units
  ) {
    this(graph, summaries, entries, units, Subtypes.index(units));
  }

  /** Creates a snapshot that holds no source units. */
  public DependencySnapshot(
      final DependencyGraph graph,
      final Map<String,
      FileSummary> summaries,
      final Set<String> entries
  ) {
    this(graph, summaries, entries, Map.of());
  }

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
    units = Map.copyOf(units);
    subtypes = Map.copyOf(subtypes);
  }

  /** Returns the view of the file at {@code path}: its direct dependencies and dependents. */
  public DependencyView view(final String path) {
    return new DependencyView(
        summariesOf(graph.dependenciesOf(path)),
        summariesOf(graph.dependentsOf(path)),
        graph.outgoingEdgesOf(path)
    );
  }

  /**
   * Returns the source units of the files that the file at {@code path} depends on, directly or
   * through others, in path order; a file that is not among the run's units is left out.
   */
  public List<SourceUnit> closureOf(final String path) {
    final Set<String> seen = new TreeSet<>(graph.closureOf(path));
    final Deque<String> pending = new ArrayDeque<>(seen);
    pending.add(path);
    while (!pending.isEmpty()) {
      for (final String subtype : subtypesDeclaredIn(pending.poll())) {
        if (!subtype.equals(path) && seen.add(subtype)) {
          pending.add(subtype);
          graph.closureOf(subtype).stream()
              .filter(other -> !other.equals(path) && seen.add(other))
              .forEach(pending::add);
        }
      }
    }
    return seen.stream().map(units::get).filter(Objects::nonNull).toList();
  }

  /** The files that extend or implement a type that the file declares, by simple name. */
  private Set<String> subtypesDeclaredIn(final String path) {
    final FileSummary summary = summaries.get(path);
    final Set<String> found = new TreeSet<>();
    if (summary != null) {
      summary.declared().forEach(
          symbol -> found.addAll(subtypes.getOrDefault(Subtypes.simple(symbol.name()), Set.of()))
      );
    }
    return found;
  }

  private List<FileSummary> summariesOf(final List<String> paths) {
    return paths.stream().map(summaries::get).filter(Objects::nonNull).toList();
  }
}
