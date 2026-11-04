package dev.ward.infrastructure.analysis.node;

import dev.ward.analysis.domain.node.NodeProjectLayout;
import dev.ward.analysis.domain.node.NodeProjectLayout.ExportEntry;
import dev.ward.analysis.domain.node.NodeProjectLayout.NodePackage;
import dev.ward.infrastructure.project.node.NodeProjectInspection;
import dev.ward.infrastructure.project.node.NodeWorkspaces;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the module layout of a Node project from its manifests: the workspace packages, and the
 * effective {@code paths} and {@code baseUrl} of each TypeScript config, following {@code extends}.
 */
public final class NodeLayouts {
  private static final String NAME = "name";
  private static final String EXPORTS = "exports";
  private static final String TYPES = "types";
  private static final String MODULE = "module";
  private static final String MAIN = "main";
  private static final String DOT = ".";

  private NodeLayouts() {}

  /**
   * Reads the layout of {@code root} for the TypeScript and JavaScript {@code files}. A manifest or
   * config that cannot be read is skipped with a warning, and its files resolve without it.
   */
  public static NodeProjectLayout read(final Path root, final Set<String> files) {
    final List<NodePackage> packages = packages(root);
    return new NodeProjectLayout(files, NodeTsConfigs.of(root, files, packages), packages);
  }

  private static List<NodePackage> packages(final Path root) {
    final List<Path> directories = new ArrayList<>();
    if (Files.isRegularFile(root.resolve(NodeProjectInspection.PACKAGE_JSON))) {
      directories.add(root);
    }
    NodeWorkspaces.members(root).stream()
        .filter(member -> !member.equals(root))
        .forEach(directories::add);
    final List<NodePackage> packages = new ArrayList<>();
    for (final Path directory : directories) {
      final Map<?, ?> manifest = NodeProjectInspection.manifest(directory);
      if (manifest.get(NAME) instanceof String name) {
        packages.add(
            new NodePackage(
                name,
                relative(root, directory),
                exports(manifest.get(EXPORTS)),
                text(manifest.get(TYPES)),
                text(manifest.get(MODULE)),
                text(manifest.get(MAIN))));
      }
    }
    return packages;
  }

  /**
   * Converts an {@code exports} field into entries by subpath; a bare string is the {@code .}
   * entry.
   */
  private static Map<String, List<ExportEntry>> exports(final Object field) {
    final Map<String, List<ExportEntry>> exports = new LinkedHashMap<>();
    if (field instanceof String target) {
      exports.put(DOT, List.of(new ExportEntry(List.of(), target)));
    } else if (field instanceof Map<?, ?> map) {
      final boolean subpaths =
          map.keySet().stream().anyMatch(key -> String.valueOf(key).startsWith(DOT));
      if (subpaths) {
        map.forEach((key, value) -> exports.put(String.valueOf(key), entries(value, List.of())));
      } else {
        exports.put(DOT, entries(map, List.of()));
      }
    }
    exports.values().removeIf(List::isEmpty);
    return exports;
  }

  /** Flattens nested conditions into entries, keeping the order the manifest gives them. */
  private static List<ExportEntry> entries(final Object value, final List<String> conditions) {
    final List<ExportEntry> entries = new ArrayList<>();
    if (value instanceof String target) {
      entries.add(new ExportEntry(conditions, target));
    } else if (value instanceof Map<?, ?> map) {
      map.forEach(
          (key, nested) -> {
            final List<String> chain = new ArrayList<>(conditions);
            chain.add(String.valueOf(key));
            entries.addAll(entries(nested, chain));
          });
    }
    return entries;
  }

  /** The path of {@code path} relative to {@code root}, with {@code /} separators; root is "". */
  static String relative(final Path root, final Path path) {
    return root.toAbsolutePath()
        .normalize()
        .relativize(path.toAbsolutePath().normalize())
        .toString()
        .replace('\\', '/');
  }

  static String text(final Object value) {
    return value instanceof String text ? text : null;
  }
}
