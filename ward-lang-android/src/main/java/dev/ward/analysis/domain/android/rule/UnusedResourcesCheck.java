package dev.ward.analysis.domain.android.rule;

import dev.ward.analysis.domain.android.AndroidSyntax;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.ProjectCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Finds a resource that nothing refers to: a string, color, dimension, style or other value of a
 * {@code values} file, or a layout, menu, drawable, color, navigation or XML file under {@code
 * res}, that no XML file names with {@code @type/name} and no Java or Kotlin file names as {@code
 * R.type.name}. A fact about the whole project, so it reads every file of the run. Limits: a
 * resource read only through {@code Resources.getIdentifier} or a generated class other than view
 * binding is reported; the library resources of other modules are not known.
 */
public final class UnusedResourcesCheck implements RuleCheck, ProjectCheck {

  private static final Comparator<ProjectMatch> ORDER =
      Comparator.comparing(ProjectMatch::path)
          .thenComparingInt(match -> match.match().span().startLine())
          .thenComparingInt(match -> match.match().span().startColumn());

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    final ResourceReferences references = new ResourceReferences();
    for (final Map.Entry<String, SyntaxNode> tree : trees.entrySet()) {
      if (isAndroid(tree.getValue())) {
        references.addXml(tree.getValue());
      } else {
        references.addCode(tree.getValue());
      }
    }
    return trees.entrySet().stream()
        .filter(tree -> isAndroid(tree.getValue()))
        .flatMap(
            tree ->
                ResourceDefinitions.of(tree.getKey(), tree.getValue()).stream()
                    .filter(definition -> !references.uses(definition.key()))
                    .map(definition -> match(tree.getKey(), definition)))
        .sorted(ORDER)
        .toList();
  }

  private static ProjectMatch match(
      final String path, final ResourceDefinitions.Definition definition) {
    final ResourceKey key = definition.key();
    return new ProjectMatch(
        path,
        new RuleMatch(
            definition.node().span(),
            "Unused resource " + key.type() + "/" + key.name() + ": nothing refers to it."));
  }

  /** Tells whether a tree is an XML file of the Android language, not a Java or Kotlin file. */
  private static boolean isAndroid(final SyntaxNode root) {
    return AndroidSyntax.isElement(root.kind()) || AndroidSyntax.DOCUMENT.equals(root.kind());
  }
}
