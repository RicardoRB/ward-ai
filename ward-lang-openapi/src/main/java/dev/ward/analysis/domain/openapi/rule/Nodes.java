package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Walks the nodes of a document the way a JSONPath does: each with the key it sits under. */
final class Nodes {

  private Nodes() {}

  /** A node and the key, or the index, it is written under; the root has an empty key. */
  record Located(String key, DocNode node, List<String> path) {}

  /**
   * Every node reachable from {@code start}, {@code start} included, each once, in file order, with
   * every reference read as its target.
   *
   * <p>shortcut: a node reached through two references is visited once, where Spectral visits it
   * once per path; upgrade when a rule must report it per path.
   */
  static List<Located> resolved(final OpenApiDocument document, final DocNode start) {
    return walk(document, start, true);
  }

  /** Every node of the file of {@code start} below it, {@code start} included, references kept. */
  static List<Located> raw(final DocNode start) {
    return walk(null, start, false);
  }

  private static List<Located> walk(
      final OpenApiDocument document, final DocNode start, final boolean follow) {
    final List<Located> found = new ArrayList<>();
    final Set<DocNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    final Deque<Located> stack = new ArrayDeque<>();
    stack.push(new Located("", follow ? document.resolve(start) : start, List.of()));
    while (!stack.isEmpty()) {
      final Located current = stack.pop();
      if (!seen.add(current.node())) {
        continue;
      }
      found.add(current);
      final List<Located> children = children(document, current.node(), current.path(), follow);
      for (int index = children.size() - 1; index >= 0; index--) {
        stack.push(children.get(index));
      }
    }
    return found;
  }

  private static List<String> extend(final List<String> path, final String key) {
    final List<String> longer = new ArrayList<>(path);
    longer.add(key);
    return longer;
  }

  private static List<Located> children(
      final OpenApiDocument document,
      final DocNode node,
      final List<String> path,
      final boolean follow) {
    final List<Located> children = new ArrayList<>();
    for (final DocNode.Entry entry : node.entries()) {
      children.add(
          new Located(
              entry.key(),
              follow ? document.resolve(entry.value()) : entry.value(),
              extend(path, entry.key())));
    }
    for (int index = 0; index < node.items().size(); index++) {
      final DocNode item = node.items().get(index);
      children.add(
          new Located(
              Integer.toString(index),
              follow ? document.resolve(item) : item,
              extend(path, Integer.toString(index))));
    }
    return children;
  }

  /** The nodes below {@code start}, itself excluded, written under {@code key}. */
  static List<DocNode> named(
      final OpenApiDocument document, final DocNode start, final String key) {
    return resolved(document, start).stream()
        .filter(located -> !located.node().equals(document.resolve(start)))
        .filter(located -> key.equals(located.key()))
        .map(Located::node)
        .toList();
  }
}
