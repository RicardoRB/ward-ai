package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeManifest;
import dev.ward.analysis.domain.node.NodeManifest.Member;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;

/**
 * Reports a dependency of a {@code package.json} section that no file of its package imports, as
 * knip reports an unused dependency. A package named in a script counts as used, a {@code @types}
 * package counts as used when the package it types is, and the root manifest counts the imports of
 * every file, since workspaces share root dependencies.
 *
 * <p>shortcut: a dependency used only by a non-TypeScript config (JSON or YAML that names it as a
 * string) is reported; upgrade by reading those configs as knip's plugins do.
 */
public final class NodeUnusedDependencyCheck implements RuleCheck, ProjectCheck {

  private static final String TYPES = "@types/";
  private static final String SCOPE_SEPARATOR = "__";
  private static final String VALUE = "{value}";

  private final String message;
  private final String section;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the dependency name
   * @param section the manifest section to check, such as {@code dependencies}
   */
  public NodeUnusedDependencyCheck(final String message, final String section) {
    this.message = Objects.requireNonNull(message, "message");
    this.section = Objects.requireNonNull(section, "section");
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
    final Map<String, NodeManifests.Manifest> manifests = NodeManifests.byDirectory(files);
    final Map<String, NodeManifests.Manifest> installed = NodeInstalledPackages.installed(files);
    final Map<String, Set<String>> imports = NodeImportUsage.importsByDirectory(trees, manifests);
    final List<ProjectMatch> found = new ArrayList<>();
    for (final NodeManifests.Manifest owner : manifests.values()) {
      final Set<String> used = usedBy(owner, imports, trees, files, installed);
      final Set<String> scripts = scriptTokens(owner.manifest());
      for (final Member dependency : owner.manifest().entries(section)) {
        if (!isUsed(dependency.key(), used, scripts)) {
          found.add(new ProjectMatch(owner.path(), match(dependency)));
        }
      }
    }
    return List.copyOf(found);
  }

  /**
   * The packages a workspace uses: the ones its files import (the root manifest counts every file),
   * the ones it uses without an import, and the peers of those that are installed.
   */
  private static Set<String> usedBy(
      final NodeManifests.Manifest owner,
      final Map<String, Set<String>> imports,
      final SortedMap<String, SyntaxNode> trees,
      final ProjectFiles files,
      final Map<String, NodeManifests.Manifest> installed) {
    final Set<String> used =
        new HashSet<>(
            owner.directory().isEmpty()
                ? everywhere(imports)
                : imports.getOrDefault(owner.directory(), Set.of()));
    used.addAll(NodeImplicitUse.of(owner, trees, files, installed));
    used.addAll(NodeImplicitUse.peersOf(used, installed));
    return used;
  }

  /** The packages any workspace imports, which the root manifest lists as used. */
  private static Set<String> everywhere(final Map<String, Set<String>> imports) {
    final Set<String> all = new HashSet<>();
    imports.values().forEach(all::addAll);
    return all;
  }

  private static boolean isUsed(
      final String name, final Set<String> used, final Set<String> scripts) {
    return used.contains(name) || scripts.contains(name) || typedPackageUsed(name, used);
  }

  /**
   * {@code @types/pkg} and {@code @types/scope__pkg} are used when {@code pkg} or
   * {@code @scope/pkg} is.
   */
  private static boolean typedPackageUsed(final String name, final Set<String> used) {
    if (!name.startsWith(TYPES)) {
      return false;
    }
    final String typed = name.substring(TYPES.length());
    final int separator = typed.indexOf(SCOPE_SEPARATOR);
    final String pkg =
        separator < 0
            ? typed
            : "@"
                + typed.substring(0, separator)
                + "/"
                + typed.substring(separator + SCOPE_SEPARATOR.length());
    return used.contains(pkg);
  }

  private static Set<String> scriptTokens(final NodeManifest manifest) {
    final Set<String> tokens = new HashSet<>();
    NodeManifests.texts(manifest, "scripts")
        .forEach(script -> tokens.addAll(List.of(script.trim().split("\\s+"))));
    return tokens;
  }

  private RuleMatch match(final Member dependency) {
    final Span at =
        new Span(
            dependency.line(), dependency.column(), dependency.line(), dependency.column(), 0, 0);
    return new RuleMatch(at, message.replace(VALUE, dependency.key()));
  }
}
