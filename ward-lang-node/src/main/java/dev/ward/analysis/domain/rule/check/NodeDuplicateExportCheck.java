package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeFileFacts;
import dev.ward.analysis.domain.node.NodeFileFacts.ExportAlias;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.stream.Collectors;

/**
 * Reports a local binding that one module exports under two or more names, as knip reports
 * duplicate exports: {@code export { a, a as b }} or {@code export default a} beside {@code export
 * { a }}. The first export of a binding is kept; each later export under another name is reported.
 *
 * <p>shortcut: groups exports by local name within one module, so a declared export ({@code export
 * const a}) that is also listed under another name is not reported; upgrade by resolving
 * declarations into the same binding.
 */
public final class NodeDuplicateExportCheck implements RuleCheck, ProjectCheck {

  private static final String VALUE = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the local name
   */
  public NodeDuplicateExportCheck(final String message) {
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
    final List<ProjectMatch> found = new ArrayList<>();
    trees.forEach((path, tree) -> duplicates(path, tree, found));
    return List.copyOf(found);
  }

  private void duplicates(
      final String path, final SyntaxNode tree, final List<ProjectMatch> found) {
    final Map<String, List<ExportAlias>> byLocal =
        NodeFileFacts.exportAliases(tree).stream()
            .collect(
                Collectors.groupingBy(ExportAlias::local, LinkedHashMap::new, Collectors.toList()));
    for (final List<ExportAlias> exports : byLocal.values()) {
      final ExportAlias first = exports.get(0);
      for (final ExportAlias later : exports.subList(1, exports.size())) {
        if (!later.exported().equals(first.exported())) {
          found.add(report(path, later));
        }
      }
    }
  }

  private ProjectMatch report(final String path, final ExportAlias later) {
    return new ProjectMatch(
        path, new RuleMatch(later.span(), message.replace(VALUE, later.local())));
  }
}
