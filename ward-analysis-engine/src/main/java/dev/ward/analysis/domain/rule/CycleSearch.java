package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.DependencyEdge;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Finds the dependency cycles between groups of files. Each file is placed in a group by {@code
 * sliceOf}, and an edge between two groups counts once, through its first edge in graph order. A
 * cycle is reported once for each group of two or more groups that depend on each other, as the
 * shortest cycle through its alphabetically first group.
 */
public final class CycleSearch {

  private static final int MIN_CYCLE_SLICES = 2;

  private CycleSearch() {}

  /**
   * One cycle: the groups in order, starting and ending with the same group, and the edge that
   * witnesses each step.
   *
   * @param slices the groups of the cycle, the first repeated last
   * @param edges one witness edge per step, from each group to the next
   */
  public record Cycle(List<String> slices, List<DependencyEdge> edges) {

    /** Copies the lists. */
    public Cycle {
      slices = List.copyOf(slices);
      edges = List.copyOf(edges);
    }
  }

  /**
   * Returns one cycle per group of two or more groups that depend on each other, among {@code
   * edges}, with the files that belong to no group left out.
   */
  public static List<Cycle> find(
      final List<DependencyEdge> edges, final Function<String, Optional<String>> sliceOf) {
    final Map<String, SortedMap<String, DependencyEdge>> witnesses = witnesses(edges, sliceOf);
    final Map<String, SortedSet<String>> successors = successors(witnesses);
    final List<Cycle> cycles = new ArrayList<>();
    for (final List<String> component : StronglyConnectedComponents.of(successors)) {
      if (component.size() >= MIN_CYCLE_SLICES) {
        cycles.add(cycle(component.getFirst(), successors, witnesses));
      }
    }
    return List.copyOf(cycles);
  }

  /** The first edge, in graph order, for each pair of groups with a dependency between them. */
  private static Map<String, SortedMap<String, DependencyEdge>> witnesses(
      final List<DependencyEdge> edges, final Function<String, Optional<String>> sliceOf) {
    final Map<String, SortedMap<String, DependencyEdge>> witnesses = new TreeMap<>();
    for (final DependencyEdge edge : edges) {
      final Optional<String> from = sliceOf.apply(edge.from());
      final Optional<String> to = sliceOf.apply(edge.to());
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

  /** The group graph: every group is a key, with the groups it depends on. */
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

  private static Cycle cycle(
      final String start,
      final Map<String, SortedSet<String>> successors,
      final Map<String, SortedMap<String, DependencyEdge>> witnesses) {
    final List<String> ring = new ArrayList<>(shortestCycle(start, successors));
    ring.add(start);
    final List<DependencyEdge> edges = new ArrayList<>();
    for (int index = 0; index + 1 < ring.size(); index++) {
      edges.add(witnesses.get(ring.get(index)).get(ring.get(index + 1)));
    }
    return new Cycle(ring, edges);
  }

  /**
   * Returns the groups of the shortest cycle from {@code start} back to itself, starting at {@code
   * start}, found by a breadth-first search that visits groups in alphabetical order.
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

  /** Whether {@code target} can be reached from {@code from}; a plain search, as groups are few. */
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
