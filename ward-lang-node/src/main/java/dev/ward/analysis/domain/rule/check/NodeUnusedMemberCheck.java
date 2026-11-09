package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.node.NodeFileFacts;
import dev.ward.analysis.domain.node.NodeFileFacts.MemberUsage;
import dev.ward.analysis.domain.node.NodeModuleResolver;
import dev.ward.analysis.domain.node.NodeProjectLayout;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;

/**
 * Reports a member of an exported enum or namespace that no file of the project reaches as {@code
 * name.member}, as knip reports unused enum and namespace members. A name that a file uses in any
 * other way (passed as a value, indexed, spread, re-exported, or namespace-imported) uses every
 * member of it, so nothing is reported for that object.
 *
 * <p>shortcut: follows named imports that resolve to the declaring file, not re-exports through a
 * barrel; upgrade by following export-from chains.
 */
public final class NodeUnusedMemberCheck implements RuleCheck, ProjectCheck {

  private static final String VALUE = "{value}";

  private final String message;
  private final String keyword;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the member name
   * @param keyword the declaration keyword to check, {@code enum} or {@code namespace}
   */
  public NodeUnusedMemberCheck(final String message, final String keyword) {
    this.message = Objects.requireNonNull(message, "message");
    this.keyword = Objects.requireNonNull(keyword, "keyword");
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
    final List<ProjectMatch> found = new ArrayList<>();
    trees.forEach(
        (path, tree) -> {
          for (final String name : declaredNames(tree)) {
            found.addAll(unusedMembers(path, name, trees, resolver));
          }
        });
    return List.copyOf(found);
  }

  /** The names this file exports as a declaration of the checked keyword. */
  private List<String> declaredNames(final SyntaxNode tree) {
    final List<String> names = new ArrayList<>();
    for (final Map.Entry<String, String> kind : NodeFileFacts.exportKinds(tree).entrySet()) {
      if (keyword.equals(kind.getValue())) {
        names.add(kind.getKey());
      }
    }
    return names;
  }

  /** The members of {@code name} in {@code path} that nothing in the project reaches. */
  private List<ProjectMatch> unusedMembers(
      final String path,
      final String name,
      final SortedMap<String, SyntaxNode> trees,
      final NodeModuleResolver resolver) {
    final MemberUsage usage = NodeMemberUses.of(path, name, trees, resolver);
    final List<ProjectMatch> found = new ArrayList<>();
    if (!usage.escaped()) {
      for (final Symbol member : NodeFileFacts.declaredMembers(trees.get(path), name, keyword)) {
        if (!usage.members().contains(member.name())) {
          found.add(report(path, member));
        }
      }
    }
    return found;
  }

  private ProjectMatch report(final String path, final Symbol member) {
    return new ProjectMatch(path, match(member.span(), member.name()));
  }

  private RuleMatch match(final Span span, final String member) {
    return new RuleMatch(span, message.replace(VALUE, member));
  }
}
