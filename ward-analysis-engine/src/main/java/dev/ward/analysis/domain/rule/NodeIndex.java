package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The nodes of one tree whose kinds some rule wants, found in one walk made on first use, so many
 * rules share it instead of walking the tree each. Not thread-safe: one unit's rules run on one
 * thread.
 */
public final class NodeIndex {
  /** An index that holds no kind, so every request misses and the rule walks the tree itself. */
  public static final NodeIndex NONE = new NodeIndex(null, Set.of());

  private static final int SINGLE = 1;

  private final SyntaxNode root;
  private final Set<String> kinds;
  private Map<String, List<Entry>> byKind;
  private int walks;
  private int found;

  /**
   * A node of an indexed kind with its ancestors.
   *
   * @param node the node
   * @param ancestors the nodes above it, root first
   * @param order the position of the node in source order among the indexed nodes
   */
  public record Entry(SyntaxNode node, List<SyntaxNode> ancestors, int order) {}

  /** Indexes the nodes of {@code kinds} below {@code root}, when first asked. */
  public NodeIndex(final SyntaxNode root, final Set<String> kinds) {
    this.root = root;
    this.kinds = Set.copyOf(Objects.requireNonNull(kinds, "kinds"));
  }

  /** How many walks of the tree this index has made. */
  public int walks() {
    return walks;
  }

  /**
   * Returns the nodes of {@code wanted} in source order, or nothing when a kind is not indexed.
   */
  public Optional<List<Entry>> visits(final Set<String> wanted) {
    if (root == null || !kinds.containsAll(wanted)) {
      return Optional.empty();
    }
    final Map<String, List<Entry>> index = index();
    if (wanted.size() == SINGLE) {
      return Optional.of(index.getOrDefault(wanted.iterator().next(), List.of()));
    }
    final List<Entry> merged = new ArrayList<>();
    wanted.forEach(kind -> merged.addAll(index.getOrDefault(kind, List.of())));
    merged.sort(Comparator.comparingInt(Entry::order));
    return Optional.of(merged);
  }

  private Map<String, List<Entry>> index() {
    if (byKind == null) {
      walks++;
      byKind = new HashMap<>();
      collect(root, new ArrayList<>());
    }
    return byKind;
  }

  private void collect(final SyntaxNode node, final List<SyntaxNode> ancestors) {
    if (kinds.contains(node.kind())) {
      byKind
          .computeIfAbsent(node.kind(), kind -> new ArrayList<>())
          .add(new Entry(node, List.copyOf(ancestors), found++));
    }
    ancestors.add(node);
    for (final SyntaxNode child : node.children()) {
      collect(child, ancestors);
    }
    ancestors.remove(ancestors.size() - 1);
  }
}
