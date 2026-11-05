package dev.ward.analysis.domain.rule.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * Finds the strongly connected components of a directed graph with an iterative Tarjan, so a deep
 * chain of slices cannot exhaust the call stack. Each component is sorted; the components come in
 * the order Tarjan closes them, so callers that need an order sort them.
 */
final class StronglyConnectedComponents {

  private final Map<String, SortedSet<String>> successors;
  private final Map<String, Integer> index = new HashMap<>();
  private final Map<String, Integer> lowLink = new HashMap<>();
  private final Set<String> onStack = new HashSet<>();
  private final Deque<String> stack = new ArrayDeque<>();
  private final List<List<String>> components = new ArrayList<>();
  private int counter;

  private StronglyConnectedComponents(final Map<String, SortedSet<String>> successors) {
    this.successors = successors;
  }

  /**
   * Returns the components of {@code successors}, where every node is a key and each value holds
   * the nodes it points to, sorted.
   */
  static List<List<String>> of(final Map<String, SortedSet<String>> successors) {
    final StronglyConnectedComponents tarjan = new StronglyConnectedComponents(successors);
    successors.keySet().stream()
        .filter(node -> !tarjan.index.containsKey(node))
        .forEach(tarjan::visit);
    return List.copyOf(tarjan.components);
  }

  /** Visits every node reachable from {@code root} that has no index yet. */
  private void visit(final String root) {
    final Deque<Frame> calls = new ArrayDeque<>();
    open(root);
    calls.push(new Frame(root, successors.get(root).iterator()));
    while (!calls.isEmpty()) {
      final Frame frame = calls.peek();
      if (frame.successorsLeft().hasNext()) {
        follow(calls, frame, frame.successorsLeft().next());
      } else {
        calls.pop();
        finish(frame.node());
        if (!calls.isEmpty()) {
          lowLink.merge(calls.peek().node(), lowLink.get(frame.node()), Math::min);
        }
      }
    }
  }

  private void follow(final Deque<Frame> calls, final Frame frame, final String next) {
    if (index.containsKey(next)) {
      if (onStack.contains(next)) {
        lowLink.merge(frame.node(), index.get(next), Math::min);
      }
    } else {
      open(next);
      calls.push(new Frame(next, successors.get(next).iterator()));
    }
  }

  private void open(final String node) {
    index.put(node, counter);
    lowLink.put(node, counter);
    counter++;
    stack.push(node);
    onStack.add(node);
  }

  /** Closes a component when {@code node} is its root, popping the members above it. */
  private void finish(final String node) {
    if (!lowLink.get(node).equals(index.get(node))) {
      return;
    }
    final List<String> component = new ArrayList<>();
    String member;
    do {
      member = stack.pop();
      onStack.remove(member);
      component.add(member);
    } while (!member.equals(node));
    component.sort(null);
    components.add(List.copyOf(component));
  }

  /** One node on the explicit call stack, with the successors still to visit. */
  private record Frame(String node, Iterator<String> successorsLeft) {}
}
