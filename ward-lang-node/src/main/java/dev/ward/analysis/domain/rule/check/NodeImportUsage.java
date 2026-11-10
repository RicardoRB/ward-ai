package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeFileFacts;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/** The packages that a project's files import, by the manifest directory that owns each file. */
final class NodeImportUsage {

  private static final String NODE_TYPES = "@types/node";

  private NodeImportUsage() {}

  /**
   * The packages that each directory's files import: a file counts toward the nearest manifest, so
   * a file under no manifest counts for none.
   */
  static Map<String, Set<String>> importsByDirectory(
      final SortedMap<String, SyntaxNode> trees,
      final Map<String, NodeManifests.Manifest> manifests) {
    final Map<String, Set<String>> imports = new HashMap<>();
    trees.forEach(
        (path, tree) ->
            NodeManifests.nearest(path, manifests)
                .ifPresent(owner -> addImports(imports, owner.directory(), tree)));
    return imports;
  }

  private static void addImports(
      final Map<String, Set<String>> imports, final String directory, final SyntaxNode tree) {
    final Set<String> packages = imports.computeIfAbsent(directory, key -> new HashSet<>());
    NodeFileFacts.specifiers(tree).stream()
        .map(module -> packageOf(module.specifier()))
        .filter(name -> !name.isEmpty())
        .forEach(packages::add);
  }

  /**
   * The package a specifier uses: a built-in uses {@code @types/node}, a bare specifier its
   * package.
   */
  private static String packageOf(final String specifier) {
    return NodeFileFacts.isNodeBuiltin(specifier)
        ? NODE_TYPES
        : NodeFileFacts.packageName(specifier);
  }
}
