package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.DependencyScope;
import dev.ward.infrastructure.dependency.LockFiles;
import dev.ward.infrastructure.project.node.NodeProjectInspection;
import dev.ward.infrastructure.project.node.NodeWorkspaces;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One module of a Node project: the root or a workspace member, and what its {@code package.json}
 * declares with the scope of each declaring section.
 *
 * @param path the module path relative to the project root, empty for the root
 * @param name the package name, empty when the manifest has none
 * @param declared the dependencies it declares by name
 */
record NodeMember(String path, String name, Map<String, Declared> declared) {
  private static final String PEERS = "peerDependencies";

  /**
   * One declared dependency.
   *
   * @param range the requirement as written
   * @param scope the widest scope of the sections that declare it
   * @param peer whether only peer dependencies declare it, which need not be locked
   */
  record Declared(String range, DependencyScope scope, boolean peer) {}

  /** Copies the declared map. */
  NodeMember {
    declared = Map.copyOf(declared);
  }

  /** Returns whether the manifest declares a package that must be locked. */
  boolean declaresPackages() {
    return declared.values().stream().anyMatch(dependency -> !dependency.peer());
  }

  /** Lists the root first, then the workspace members. */
  static List<NodeMember> read(final Path root) {
    final List<NodeMember> members = new ArrayList<>();
    members.add(read(root, ""));
    NodeWorkspaces.members(root).stream()
        .filter(dir -> !dir.equals(root))
        .forEach(dir -> members.add(read(dir, LockFiles.relative(root, dir))));
    return members;
  }

  private static NodeMember read(final Path dir, final String path) {
    final Map<?, ?> manifest = NodeProjectInspection.manifest(dir);
    final Map<String, Declared> declared = new LinkedHashMap<>();
    section(manifest, declared, "dependencies", DependencyScope.RUNTIME);
    section(manifest, declared, "optionalDependencies", DependencyScope.RUNTIME);
    section(manifest, declared, PEERS, DependencyScope.RUNTIME);
    section(manifest, declared, "devDependencies", DependencyScope.DEVELOPMENT);
    return new NodeMember(path, manifest.get("name") instanceof String name ? name : "", declared);
  }

  private static void section(
      final Map<?, ?> manifest,
      final Map<String, Declared> declared,
      final String section,
      final DependencyScope scope) {
    if (manifest.get(section) instanceof Map<?, ?> dependencies) {
      dependencies.forEach(
          (name, range) ->
              declared.merge(
                  String.valueOf(name),
                  new Declared(String.valueOf(range), scope, PEERS.equals(section)),
                  NodeMember::wider));
    }
  }

  private static Declared wider(final Declared first, final Declared second) {
    return new Declared(
        first.range(),
        DependencyScope.widest(first.scope(), second.scope()),
        first.peer() && second.peer());
  }
}
