package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeFileFacts;
import dev.ward.analysis.domain.node.NodeIdentifiers;
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
 * Reports an exported name that no file of the project imports, as knip reports unused exports
 * (values) or unused exported types. Exports of entry files are not reported, as knip does by
 * default. A namespace import ({@code import * as ns}) uses every export.
 *
 * <p>shortcut: a name counts as used when any file imports that name from anywhere, so an export
 * imported under the same name from another module is not reported; upgrade by resolving each
 * import to the file it names.
 *
 * <p>shortcut: a CommonJS export assigned as {@code exports.name = ...} is not read as an export,
 * so it is never reported; upgrade by reading those member assignments as knip's CommonJS support
 * does.
 */
public final class NodeUnusedExportCheck implements RuleCheck, ProjectCheck {

  private static final String NAMESPACE = "*";
  private static final Set<String> TYPE_KEYWORDS = Set.of("type", "interface");

  private final String message;
  private final boolean types;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the export name
   * @param types true to report unused exported types, false to report unused exported values
   */
  public NodeUnusedExportCheck(final String message, final boolean types) {
    this.message = Objects.requireNonNull(message, "message");
    this.types = types;
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
    final Set<String> imported = new HashSet<>();
    trees.values().forEach(tree -> imported.addAll(NodeFileFacts.importedMembers(tree)));
    final boolean everything = imported.contains(NAMESPACE);
    final Set<String> entries = NodeEntryPoints.entryPaths(files, trees.keySet());
    final List<ProjectMatch> found = new ArrayList<>();
    trees.forEach(
        (path, tree) -> {
          if (entries.contains(path)) {
            return;
          }
          final Map<String, String> kinds = NodeFileFacts.exportKinds(tree);
          final Map<String, Integer> names = NodeIdentifiers.counts(tree);
          for (final Symbol export : NodeFileFacts.exports(tree)) {
            if (isType(kinds.get(export.name())) == types
                && !everything
                && !imported.contains(export.name())
                && !(types && namedAgainIn(names, export.name()))) {
              found.add(
                  new ProjectMatch(
                      path,
                      new RuleMatch(export.span(), message.replace("{value}", export.name()))));
            }
          }
        });
    return List.copyOf(found);
  }

  /**
   * Tells whether a type is named again in its own file, past its declaration, as knip counts a
   * type that the file itself refers to as used.
   */
  private static boolean namedAgainIn(final Map<String, Integer> names, final String name) {
    return names.getOrDefault(name, 0) > 1;
  }

  private static boolean isType(final String keyword) {
    return keyword != null && TYPE_KEYWORDS.contains(keyword);
  }
}
