package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeFileFacts;
import dev.ward.analysis.domain.node.NodeFileFacts.ModuleSpecifier;
import dev.ward.analysis.domain.node.NodeManifest;
import dev.ward.analysis.domain.node.NodeManifest.Member;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;

/**
 * Reports a bare import of a package that the nearest {@code package.json} of the importing file
 * does not list in any dependency section, as knip reports an unlisted dependency. A file with no
 * manifest above it, a Node built-in and a {@code #} subpath import are never findings.
 *
 * <p>shortcut: checks the nearest manifest only, so a dependency that only the root manifest lists
 * is reported in a workspace package; a workspace-aware check would read the root manifest too.
 */
public final class NodeUnlistedImportCheck implements RuleCheck, ProjectCheck {

  private static final String MANIFEST = "package.json";
  private static final String NAME = "name";
  private static final String VALUE = "{value}";
  private static final List<String> SECTIONS =
      List.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the package name
   */
  public NodeUnlistedImportCheck(final String message) {
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
    final Map<String, Set<String>> listed = listedByDirectory(files);
    final List<ProjectMatch> found = new ArrayList<>();
    trees.forEach(
        (path, tree) -> {
          final Optional<Set<String>> declared = nearest(path, listed);
          if (declared.isEmpty()) {
            return;
          }
          for (final ModuleSpecifier module : NodeFileFacts.specifiers(tree)) {
            final String name = NodeFileFacts.packageName(module.specifier());
            if (!name.isEmpty() && !declared.get().contains(name)) {
              found.add(
                  new ProjectMatch(
                      path, new RuleMatch(module.span(), message.replace(VALUE, name))));
            }
          }
        });
    return List.copyOf(found);
  }

  /** Each manifest's directory, with the names it lists in any dependency section. */
  private static Map<String, Set<String>> listedByDirectory(final ProjectFiles files) {
    final Map<String, Set<String>> listed = new HashMap<>();
    for (final String path : files.paths()) {
      if (MANIFEST.equals(path) || path.endsWith("/" + MANIFEST)) {
        files
            .text(path)
            .flatMap(NodeManifest::parse)
            .ifPresent(manifest -> listed.put(directoryOf(path), listedNames(manifest)));
      }
    }
    return listed;
  }

  /** The names a manifest lists, and its own name, which a file of the package may import. */
  private static Set<String> listedNames(final NodeManifest manifest) {
    final Set<String> names = new HashSet<>();
    SECTIONS.forEach(section -> manifest.entries(section).forEach(entry -> names.add(entry.key())));
    manifest
        .member(NAME)
        .map(Member::value)
        .map(NodeEntryPoints::textLeaves)
        .ifPresent(names::addAll);
    return names;
  }

  /** The listed names of the nearest manifest at or above {@code path}, or empty when none. */
  private static Optional<Set<String>> nearest(
      final String path, final Map<String, Set<String>> listed) {
    String directory = directoryOf(path);
    while (true) {
      final Set<String> names = listed.get(directory);
      if (names != null) {
        return Optional.of(names);
      }
      if (directory.isEmpty()) {
        return Optional.empty();
      }
      directory = directoryOf(directory);
    }
  }

  private static String directoryOf(final String path) {
    final int slash = path.lastIndexOf('/');
    return slash < 0 ? "" : path.substring(0, slash);
  }
}
