package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FlowStep;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The {@code ward:architecture/cycles} rule: collapses the file graph into slices and reports each
 * group of two or more slices that depend on each other in a cycle, once. The finding sits at the
 * edge that leaves the group's alphabetically first slice, and its path lists one step per slice
 * edge of the shortest cycle through that slice.
 */
public final class SliceCycleRule implements GraphRule {

  /** The id of the rule, the same in every language. */
  public static final String ID = "ward:architecture/cycles";

  private static final int MIN_CYCLE_SLICES = 2;

  private final RuleDefinition definition;
  private final SliceMembership membership;

  /**
   * Creates the rule that slices the files below {@code roots}, described by {@code definition}.
   *
   * @throws NullPointerException if a field is missing
   */
  public SliceCycleRule(final RuleDefinition definition, final List<String> roots) {
    this.definition = Objects.requireNonNull(definition, "definition");
    this.membership = new SliceMembership(List.copyOf(roots));
  }

  @Override
  public RuleDefinition definition() {
    return definition;
  }

  @Override
  public List<GraphMatch> matches(final DependencyGraph graph) {
    final Map<String, SortedMap<String, DependencyEdge>> witnesses = witnesses(graph);
    final Map<String, SortedSet<String>> successors = successors(witnesses);
    final List<GraphMatch> matches = new ArrayList<>();
    for (final List<String> component : StronglyConnectedComponents.of(successors)) {
      if (component.size() >= MIN_CYCLE_SLICES) {
        matches.add(cycleMatch(component.getFirst(), successors, witnesses));
      }
    }
    return List.copyOf(matches);
  }

  /** The first edge, in graph order, for each pair of slices with a dependency between them. */
  private Map<String, SortedMap<String, DependencyEdge>> witnesses(final DependencyGraph graph) {
    final Map<String, SortedMap<String, DependencyEdge>> witnesses = new TreeMap<>();
    for (final DependencyEdge edge : graph.edges()) {
      final Optional<String> from = membership.sliceOf(edge.from());
      final Optional<String> to = membership.sliceOf(edge.to());
      if (from.isPresent() && to.isPresent() && !from.equals(to)) {
        record(witnesses, from.get(), to.get(), edge);
      }
    }
    return witnesses;
  }

  /** Keeps {@code edge} as the witness of {@code from} to {@code to} unless one is kept already. */
  private static void record(
      final Map<String, SortedMap<String, DependencyEdge>> witnesses,
      final String from,
      final String to,
      final DependencyEdge edge) {
    witnesses.computeIfAbsent(from, slice -> new TreeMap<>()).putIfAbsent(to, edge);
  }

  /** The slice graph: every slice is a key, with the slices it depends on. */
  private static Map<String, SortedSet<String>> successors(
      final Map<String, SortedMap<String, DependencyEdge>> witnesses) {
    final Map<String, SortedSet<String>> successors = new TreeMap<>();
    witnesses.forEach(
        (from, targets) -> {
          successors.computeIfAbsent(from, slice -> new TreeSet<>()).addAll(targets.keySet());
          targets.keySet().forEach(to -> successors.computeIfAbsent(to, slice -> new TreeSet<>()));
        });
    return successors;
  }

  private static GraphMatch cycleMatch(
      final String start,
      final Map<String, SortedSet<String>> successors,
      final Map<String, SortedMap<String, DependencyEdge>> witnesses) {
    final List<String> ring = new ArrayList<>(shortestCycle(start, successors));
    ring.add(start);
    final List<FlowStep> steps = new ArrayList<>();
    for (int index = 0; index + 1 < ring.size(); index++) {
      final DependencyEdge edge = witnesses.get(ring.get(index)).get(ring.get(index + 1));
      steps.add(
          new FlowStep(
              edge.reference().span(),
              ring.get(index) + " depends on " + ring.get(index + 1),
              edge.from()));
    }
    final DependencyEdge first = witnesses.get(start).get(ring.get(1));
    return new GraphMatch(
        first.from(),
        new RuleMatch(
            first.reference().span(), "slices form a cycle: " + String.join(" -> ", ring), steps));
  }

  /**
   * Returns the slices of the shortest cycle from {@code start} back to itself, starting at {@code
   * start}, found by a breadth-first search that visits slices in alphabetical order.
   */
  private static List<String> shortestCycle(
      final String start, final Map<String, SortedSet<String>> successors) {
    final Map<String, String> parent = new HashMap<>();
    final Set<String> seen = new HashSet<>(Set.of(start));
    final Deque<String> queue = new ArrayDeque<>(List.of(start));
    while (!queue.isEmpty()) {
      final String node = queue.poll();
      for (final String next : successors.get(node)) {
        if (next.equals(start)) {
          return pathTo(node, start, parent);
        }
        if (seen.add(next) && inComponent(next, start, successors)) {
          parent.put(next, node);
          queue.add(next);
        }
      }
    }
    throw new IllegalStateException("no cycle through slice " + start);
  }

  private static boolean inComponent(
      final String node, final String start, final Map<String, SortedSet<String>> successors) {
    return reaches(node, start, successors) && reaches(start, node, successors);
  }

  private static List<String> pathTo(
      final String node, final String start, final Map<String, String> parent) {
    final List<String> path = new ArrayList<>();
    String current = node;
    path.add(current);
    while (!current.equals(start)) {
      current = parent.get(current);
      path.add(current);
    }
    Collections.reverse(path);
    return path;
  }

  /** Whether {@code target} can be reached from {@code from}; a plain search, as slices are few. */
  private static boolean reaches(
      final String from, final String target, final Map<String, SortedSet<String>> successors) {
    final Set<String> seen = new HashSet<>(Set.of(from));
    final Deque<String> queue = new ArrayDeque<>(List.of(from));
    while (!queue.isEmpty()) {
      final String node = queue.poll();
      if (node.equals(target)) {
        return true;
      }
      for (final String next : successors.get(node)) {
        if (seen.add(next)) {
          queue.add(next);
        }
      }
    }
    return false;
  }
}
