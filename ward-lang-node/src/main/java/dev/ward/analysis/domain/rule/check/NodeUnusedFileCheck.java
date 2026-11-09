package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeFileFacts;
import dev.ward.analysis.domain.node.NodeFileFacts.ModuleSpecifier;
import dev.ward.analysis.domain.node.NodeModuleResolver;
import dev.ward.analysis.domain.node.NodeProjectLayout;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;

/**
 * Reports a file that no entry point reaches through relative imports, as knip reports unused
 * files. Entry points are the manifest fields that name a file, and by convention index, test and
 * config files.
 *
 * <p>shortcut: reaches files through relative specifiers only, and treats built output (for example
 * {@code dist}) as no entry; upgrade by mapping build output to its source and reading framework
 * plugins.
 */
public final class NodeUnusedFileCheck implements RuleCheck, ProjectCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the start of each unused file
   */
  public NodeUnusedFileCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(
      final SortedMap<String, SyntaxNode> trees, final ProjectFiles files) {
    final NodeModuleResolver resolver =
        new NodeModuleResolver(new NodeProjectLayout(trees.keySet(), List.of(), List.of()));
    final Set<String> reached = new HashSet<>();
    final Deque<String> queue = new ArrayDeque<>(NodeManifests.entryPaths(files, trees.keySet()));
    while (!queue.isEmpty()) {
      final String path = queue.poll();
      if (reached.add(path)) {
        for (final ModuleSpecifier module : NodeFileFacts.specifiers(trees.get(path))) {
          if (module.specifier().startsWith(".")) {
            resolver.resolve(path, module.specifier()).ifPresent(queue::add);
          }
        }
      }
    }
    final Map<String, NodeManifests.Manifest> manifests = NodeManifests.byDirectory(files);
    final List<ProjectMatch> found = new ArrayList<>();
    for (final String path : trees.keySet()) {
      if (!reached.contains(path) && !inFrameworkWorkspace(path, manifests)) {
        found.add(new ProjectMatch(path, new RuleMatch(new Span(1, 1, 1, 1, 0, 0), message)));
      }
    }
    return List.copyOf(found);
  }

  /** A file of a framework workspace is silent: its entries follow the framework's conventions. */
  private static boolean inFrameworkWorkspace(
      final String path, final Map<String, NodeManifests.Manifest> manifests) {
    return NodeManifests.nearest(path, manifests)
        .filter(NodeFrameworks::isFrameworkWorkspace)
        .isPresent();
  }
}
