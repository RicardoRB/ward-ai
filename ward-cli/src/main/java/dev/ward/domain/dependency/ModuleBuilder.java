package dev.ward.domain.dependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns the nodes, edges and declarations a source read into the entries of one module: direct
 * wins, the widest scope wins, equal packages are one entry. Readers feed it and never compute
 * relation, scope or {@code requiredBy} themselves.
 */
public final class ModuleBuilder {
  private final String source;
  private final boolean propagateScope;
  private final Map<PackageId, Node> nodes = new HashMap<>();

  /**
   * Creates a builder for entries read from {@code source}.
   *
   * @param source the project-relative lockfile or build file
   * @param propagateScope whether a package takes the widest scope of the packages requiring it;
   *     {@code false} for a source whose scopes are already mediated (Maven, Gradle)
   */
  public ModuleBuilder(final String source, final boolean propagateScope) {
    this.source = source;
    this.propagateScope = propagateScope;
  }

  /** Adds a package; adding it again keeps it {@code released} only if both adds say so. */
  public ModuleBuilder add(final PackageId id, final boolean released) {
    final Node node = node(id);
    node.released = node.seen ? node.released && released : released;
    node.seen = true;
    return this;
  }

  /** Records that {@code parent} requires {@code child}. Both are added if unknown. */
  public ModuleBuilder edge(final PackageId parent, final PackageId child) {
    node(parent);
    node(child).requiredBy.add(parent);
    return this;
  }

  /** Marks {@code id} as declared by the module, in {@code declaredIn}, with {@code scope}. */
  public ModuleBuilder direct(
      final PackageId id, final DependencyScope scope, final String declaredIn) {
    final Node node = node(id);
    node.direct = true;
    node.declaredIn = node.declaredIn.isEmpty() ? declaredIn : min(node.declaredIn, declaredIn);
    return scope(id, scope);
  }

  /** Gives {@code id} a scope of its own; the widest of all it is given wins. */
  public ModuleBuilder scope(final PackageId id, final DependencyScope scope) {
    final Node node = node(id);
    node.scope = node.scope == null ? scope : DependencyScope.widest(node.scope, scope);
    return this;
  }

  /** Records a version requested for {@code id} before mediation. */
  public ModuleBuilder requested(final PackageId id, final RequestedVersion request) {
    node(id).requested.add(request);
    return this;
  }

  /**
   * Builds the module.
   *
   * @throws IllegalStateException if a package has no scope, from itself or from a requirer
   */
  public ModuleDependencies build(final String modulePath) {
    if (propagateScope) {
      propagate();
    }
    final List<Dependency> entries = new ArrayList<>();
    for (final Map.Entry<PackageId, Node> entry : nodes.entrySet()) {
      final Node node = entry.getValue();
      if (node.scope == null) {
        throw new IllegalStateException("No scope for " + entry.getKey());
      }
      entries.add(
          new Dependency(
              entry.getKey(),
              node.direct ? DependencyRelation.DIRECT : DependencyRelation.TRANSITIVE,
              node.scope,
              node.released,
              source,
              node.direct ? node.declaredIn : "",
              node.requiredBy,
              node.requested));
    }
    return new ModuleDependencies(modulePath, entries);
  }

  /** Pushes each scope down the edges until no package gets a wider one; cycles end there. */
  private void propagate() {
    final Map<PackageId, Set<PackageId>> children = new HashMap<>();
    nodes.forEach(
        (id, node) ->
            node.requiredBy.forEach(
                parent -> children.computeIfAbsent(parent, key -> new TreeSet<>()).add(id)));
    final Deque<PackageId> work = new ArrayDeque<>();
    nodes.forEach(
        (id, node) -> {
          if (node.scope != null) {
            work.add(id);
          }
        });
    while (!work.isEmpty()) {
      final PackageId parent = work.poll();
      for (final PackageId child : children.getOrDefault(parent, Set.of())) {
        final Node node = nodes.get(child);
        final DependencyScope wider =
            node.scope == null
                ? nodes.get(parent).scope
                : DependencyScope.widest(node.scope, nodes.get(parent).scope);
        if (wider != node.scope) {
          node.scope = wider;
          work.add(child);
        }
      }
    }
  }

  private Node node(final PackageId id) {
    return nodes.computeIfAbsent(id, key -> new Node());
  }

  private static String min(final String first, final String second) {
    return first.compareTo(second) <= 0 ? first : second;
  }

  /** The mutable state of one package while a source is read. */
  private static final class Node {
    private boolean seen;
    private boolean released = true;
    private boolean direct;
    private String declaredIn = "";
    private DependencyScope scope;
    private final Set<PackageId> requiredBy = new TreeSet<>();
    private final Set<RequestedVersion> requested = new HashSet<>();
  }
}
