package dev.ward.analysis.domain.graph;

import dev.ward.analysis.domain.ir.DependencyEdge;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The resolved dependencies of one run, with the edges in both directions. The edges are sorted on
 * construction, so the graph does not depend on the order they were given in, and two graphs with
 * the same edges are equal.
 */
public final class DependencyGraph {
  private static final Comparator<DependencyEdge> ORDER =
      Comparator.comparing(DependencyEdge::from)
          .thenComparing(DependencyEdge::to)
          .thenComparing(edge -> edge.reference().specifier())
          .thenComparingInt(edge -> edge.reference().span().startOffset())
          .thenComparingInt(edge -> edge.reference().span().endOffset());

  private final List<DependencyEdge> edges;
  private final Map<String, List<String>> outgoing;
  private final Map<String, List<String>> incoming;
  private final Map<String, List<DependencyEdge>> outgoingEdges;

  /**
   * Builds the graph from {@code edges}.
   *
   * @throws NullPointerException if the list or an edge is missing
   */
  public DependencyGraph(final List<DependencyEdge> edges) {
    this.edges = edges.stream().sorted(ORDER).toList();
    this.outgoing = adjacency(DependencyEdge::from, DependencyEdge::to);
    this.incoming = adjacency(DependencyEdge::to, DependencyEdge::from);
    this.outgoingEdges =
        this.edges.stream()
            .collect(
                Collectors.groupingBy(DependencyEdge::from, TreeMap::new, Collectors.toList()));
  }

  /** Returns every edge, sorted by source, target and reference. */
  public List<DependencyEdge> edges() {
    return List.copyOf(edges);
  }

  /** Returns the sorted paths that {@code path} depends on directly. */
  public List<String> dependenciesOf(final String path) {
    return outgoing.getOrDefault(path, List.of());
  }

  /** Returns the edges that leave {@code path}, in the graph's order, with their references. */
  public List<DependencyEdge> outgoingEdgesOf(final String path) {
    return outgoingEdges.getOrDefault(path, List.of());
  }

  /** Returns the sorted paths that depend on {@code path} directly. */
  public List<String> dependentsOf(final String path) {
    return incoming.getOrDefault(path, List.of());
  }

  private Map<String, List<String>> adjacency(
      final Function<DependencyEdge, String> key, final Function<DependencyEdge, String> value) {
    return edges.stream()
        .collect(
            Collectors.groupingBy(
                key,
                TreeMap::new,
                Collectors.mapping(
                    value,
                    Collectors.collectingAndThen(
                        Collectors.toCollection(TreeSet::new), List::copyOf))));
  }

  @Override
  public boolean equals(final Object other) {
    return other instanceof DependencyGraph graph && edges.equals(graph.edges);
  }

  @Override
  public int hashCode() {
    return edges.hashCode();
  }
}
