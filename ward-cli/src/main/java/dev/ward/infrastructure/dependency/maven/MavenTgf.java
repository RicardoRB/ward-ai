package dev.ward.infrastructure.dependency.maven;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.dependency.RequestedVersion;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the inventory from the verbose TGF log of a whole reactor. The verbose tree also lists the
 * versions Maven omitted, which is the unmediated tree: each edge says which version its parent
 * asked for, and those requests are kept per package.
 */
final class MavenTgf {
  private static final String POM = "pom.xml";

  /** A module's block with the reactor's coordinates and the POM that declares its packages. */
  private record Located(TgfBlock block, Set<String> reactor, String pom) {}

  private MavenTgf() {}

  /**
   * Builds the inventory from the tool's {@code output}.
   *
   * @param modules the module paths by {@code groupId:artifactId}
   * @throws DependencyInventoryException if the output holds no module or a module is unknown
   */
  static DependencyInventory read(
      final String project, final String output, final Map<String, String> modules) {
    final List<TgfBlock> blocks = TgfBlock.parse(output);
    if (blocks.isEmpty()) {
      throw failure(project, "the output holds no dependency tree");
    }
    final Set<String> reactor = new HashSet<>(modules.keySet());
    return new DependencyInventory(
        blocks.stream().map(block -> module(project, block, reactor, modules)).toList());
  }

  private static DependencyInventoryException failure(final String project, final String reason) {
    return new DependencyInventoryException(
        project, "the Maven dependency tree", reason, "run the Maven build to see why");
  }

  private static ModuleDependencies module(
      final String project,
      final TgfBlock block,
      final Set<String> reactor,
      final Map<String, String> modules) {
    final TgfNode self = block.nodes().get(block.root());
    if (self == null || !modules.containsKey(self.name())) {
      throw failure(project, "module " + block.artifactId() + " is not in the reactor's POMs");
    }
    final String path = modules.get(self.name());
    final String pom = path.isEmpty() ? POM : path + "/" + POM;
    final ModuleBuilder builder = new ModuleBuilder(pom, false);
    final Located located = new Located(block, reactor, pom);
    final Map<String, PackageId> resolved = new HashMap<>();
    block
        .nodes()
        .forEach(
            (key, node) -> {
              if (!node.omitted() && counts(located, key, node)) {
                resolved.put(node.name(), id(node));
              }
            });
    block
        .nodes()
        .forEach(
            (key, node) -> {
              final PackageId target = resolved.get(node.name());
              if (target != null && counts(located, key, node)) {
                request(builder, block, key, target);
                if (!node.omitted()) {
                  entry(builder, located, key, target);
                }
              }
            });
    return builder.build(path);
  }

  /** Whether a node is a dependency of the module: not the module, a sibling or a cycle marker. */
  private static boolean counts(final Located located, final String key, final TgfNode node) {
    return !key.equals(located.block().root())
        && !located.reactor().contains(node.name())
        && !node.cycle();
  }

  /** Records the version a node's parent asked for, kept even when Maven omitted the node. */
  private static void request(
      final ModuleBuilder builder, final TgfBlock block, final String key, final PackageId target) {
    final String parent = block.parent(key);
    final String version = block.nodes().get(key).version();
    builder.requested(
        target,
        parent.equals(block.root())
            ? RequestedVersion.byModule(version)
            : RequestedVersion.by(id(block.nodes().get(parent)), version));
  }

  /** Adds the entry a node that Maven kept stands for, with its direct flag or its requirer. */
  private static void entry(
      final ModuleBuilder builder,
      final Located located,
      final String key,
      final PackageId target) {
    final TgfBlock block = located.block();
    final TgfNode node = block.nodes().get(key);
    builder.add(target, released(node)).scope(target, scope(node.scope()));
    if (block.parent(key).equals(block.root())) {
      builder.direct(target, scope(node.scope()), located.pom());
    } else {
      final String effective = effectiveParent(located, key);
      if (!effective.equals(block.root())) {
        builder.edge(id(block.nodes().get(effective)), target);
      }
    }
  }

  /** Climbs over reactor siblings, whose own dependencies belong to the requiring module. */
  private static String effectiveParent(final Located located, final String key) {
    final TgfBlock block = located.block();
    String parent = block.parent(key);
    while (!parent.equals(block.root())
        && located.reactor().contains(block.nodes().get(parent).name())) {
      parent = block.parent(parent);
    }
    return parent;
  }

  private static PackageId id(final TgfNode node) {
    return new PackageId(Ecosystem.MAVEN, node.name(), node.version());
  }

  private static boolean released(final TgfNode node) {
    return !node.version().endsWith("-SNAPSHOT") && !"system".equals(node.scope());
  }

  private static DependencyScope scope(final String mavenScope) {
    return switch (mavenScope) {
      case "test" -> DependencyScope.TEST;
      case "provided", "system" -> DependencyScope.PROVIDED;
      default -> DependencyScope.RUNTIME;
    };
  }
}
