package dev.ward.infrastructure.dependency.maven;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.ModuleBuilder;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.dependency.RequestedVersion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the log of {@code dependency:tree -DoutputType=tgf -Dverbose} for a whole reactor. Each
 * module is a block that starts at a {@code dependency:<version>:tree (default-cli) @ <artifactId>}
 * header and holds a node list, a {@code #} line and an edge list. The verbose tree also lists the
 * versions Maven omitted, which is the unmediated tree: each edge says which version its parent
 * asked for.
 */
final class MavenTgf {
  private static final String INFO = "[INFO] ";
  private static final Pattern HEADER =
      Pattern.compile("^-+\\s+\\S+:tree\\s+\\([^)]*\\)\\s+@\\s+(\\S+)\\s+-+$");
  private static final Pattern NODE = Pattern.compile("^(-?\\d+) (\\S.*:.*)$");
  private static final Pattern EDGE = Pattern.compile("^(-?\\d+) (-?\\d+) (\\S+)$");
  private static final String SEPARATOR = "#";
  private static final int MIN_COORDINATE_PARTS = 5;
  private static final String POM = "pom.xml";

  /** One tree node. */
  private record Node(String name, String version, String scope, boolean omitted, boolean cycle) {}

  /** One module's block. */
  private static final class Block {
    private final String artifactId;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, String> parents = new HashMap<>();
    private final List<String[]> edges = new ArrayList<>();
    private String root;
    private boolean edgesStarted;

    Block(final String artifactId) {
      this.artifactId = artifactId;
    }
  }

  private MavenTgf() {}

  /**
   * Builds the inventory from the tool's {@code output}.
   *
   * @param modules the module paths by {@code groupId:artifactId}
   * @throws DependencyInventoryException if the output holds no module or a module is unknown
   */
  static DependencyInventory read(
      final String project, final String output, final Map<String, String> modules) {
    final List<Block> blocks = blocks(output);
    if (blocks.isEmpty()) {
      throw failure(project, "the output holds no dependency tree");
    }
    final Set<String> reactor = new HashSet<>(modules.keySet());
    final List<ModuleDependencies> result = new ArrayList<>();
    for (final Block block : blocks) {
      final Node root = block.nodes.get(block.root);
      if (root == null || !modules.containsKey(root.name())) {
        throw failure(project, "module " + block.artifactId + " is not in the reactor's POMs");
      }
      final String path = modules.get(root.name());
      result.add(module(block, reactor, path));
    }
    return new DependencyInventory(result);
  }

  private static DependencyInventoryException failure(final String project, final String reason) {
    return new DependencyInventoryException(
        project, "the Maven dependency tree", reason, "run the Maven build to see why");
  }

  private static List<Block> blocks(final String output) {
    final List<Block> blocks = new ArrayList<>();
    Block current = null;
    for (final String raw : output.lines().toList()) {
      if (!raw.startsWith(INFO)) {
        continue;
      }
      final String line = raw.substring(INFO.length()).strip();
      final Matcher header = HEADER.matcher(line);
      if (header.matches()) {
        current = new Block(header.group(1));
        blocks.add(current);
      } else if (current != null) {
        add(current, line);
      }
    }
    return blocks;
  }

  private static void add(final Block block, final String line) {
    if (SEPARATOR.equals(line)) {
      block.edgesStarted = true;
      return;
    }
    final Matcher edge = EDGE.matcher(line);
    if (block.edgesStarted && edge.matches()) {
      block.edges.add(new String[] {edge.group(1), edge.group(2)});
      block.parents.put(edge.group(2), edge.group(1));
      return;
    }
    final Matcher node = NODE.matcher(line);
    if (!block.edgesStarted && node.matches()) {
      block.nodes.put(node.group(1), label(node.group(2)));
      if (block.root == null) {
        block.root = node.group(1);
      }
    }
  }

  /** Parses {@code g:a:type[:classifier]:version[:scope]}, possibly omitted in parentheses. */
  private static Node label(final String text) {
    final boolean omitted = text.startsWith("(");
    final String body = omitted ? text.substring(1, text.indexOf(" - ")) : text;
    final String reason = omitted ? text.substring(text.indexOf(" - ")) : "";
    final String[] parts = body.split(":");
    final boolean hasScope = parts.length >= MIN_COORDINATE_PARTS;
    final String version = parts[parts.length - (hasScope ? 2 : 1)];
    return new Node(
        parts[0] + ":" + parts[1],
        version,
        hasScope ? parts[parts.length - 1] : "",
        omitted,
        reason.contains("cycle"));
  }

  private static ModuleDependencies module(
      final Block block, final Set<String> reactor, final String path) {
    final String pom = path.isEmpty() ? POM : path + "/" + POM;
    final ModuleBuilder builder = new ModuleBuilder(pom, false);
    final Map<String, PackageId> resolved = new HashMap<>();
    block.nodes.forEach(
        (id, node) -> {
          if (!node.omitted() && !id.equals(block.root) && !reactor.contains(node.name())) {
            resolved.put(node.name(), id(node));
          }
        });
    for (final Map.Entry<String, Node> entry : block.nodes.entrySet()) {
      final Node node = entry.getValue();
      if (entry.getKey().equals(block.root) || reactor.contains(node.name()) || node.cycle()) {
        continue;
      }
      final PackageId target = resolved.get(node.name());
      if (target == null) {
        continue;
      }
      final String actual = block.parents.getOrDefault(entry.getKey(), block.root);
      final boolean byModule = actual.equals(block.root);
      final PackageId requester = byModule ? null : id(block.nodes.get(actual));
      builder.requested(
          target,
          byModule
              ? RequestedVersion.byModule(node.version())
              : RequestedVersion.by(requester, node.version()));
      if (!node.omitted()) {
        builder.add(target, released(node)).scope(target, scope(node.scope()));
        if (byModule) {
          builder.direct(target, scope(node.scope()), pom);
        } else {
          final String effective = effectiveParent(block, reactor, entry.getKey());
          if (!effective.equals(block.root)) {
            builder.edge(id(block.nodes.get(effective)), target);
          }
        }
      }
    }
    return builder.build(path);
  }

  /** Climbs over reactor siblings, whose own dependencies belong to the requiring module. */
  private static String effectiveParent(
      final Block block, final Set<String> reactor, final String id) {
    String parent = block.parents.getOrDefault(id, block.root);
    while (!parent.equals(block.root) && reactor.contains(block.nodes.get(parent).name())) {
      parent = block.parents.getOrDefault(parent, block.root);
    }
    return parent;
  }

  private static PackageId id(final Node node) {
    return new PackageId(Ecosystem.MAVEN, node.name(), node.version());
  }

  private static boolean released(final Node node) {
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
