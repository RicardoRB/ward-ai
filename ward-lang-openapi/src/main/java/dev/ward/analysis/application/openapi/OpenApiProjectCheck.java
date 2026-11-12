package dev.ward.analysis.application.openapi;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.openapi.LoadResult;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocuments;
import dev.ward.analysis.domain.openapi.YamlSyntax;
import dev.ward.analysis.domain.openapi.rule.OpenApiFinding;
import dev.ward.analysis.domain.openapi.rule.OpenApiRule;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.ProjectCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * Runs an OpenAPI rule as a check over the whole run. A rule's answer can depend on referenced
 * files, so it is a project check: it builds the document of each entry file (a file with an {@code
 * openapi} key) from every tree of the run, once per run, never per unit. A document that does not
 * load runs no rule; the gate reports why.
 */
final class OpenApiProjectCheck implements RuleCheck, ProjectCheck {

  private static final String ENTRY_KEY = "openapi";

  private static final Comparator<ProjectMatch> ORDER =
      Comparator.comparing(ProjectMatch::path)
          .thenComparingInt(match -> match.match().span().startLine())
          .thenComparingInt(match -> match.match().span().startColumn())
          .thenComparing(match -> match.match().message());

  private final OpenApiRule rule;

  OpenApiProjectCheck(final OpenApiRule rule) {
    this.rule = rule;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    final Set<ProjectMatch> matches = new LinkedHashSet<>();
    for (final Map.Entry<String, SyntaxNode> tree : trees.entrySet()) {
      if (isEntry(tree.getValue())) {
        final LoadResult loaded = OpenApiDocuments.fromTrees(tree.getKey(), trees);
        loaded.document().ifPresent(document -> matches.addAll(run(document)));
      }
    }
    return matches.stream().sorted(ORDER).toList();
  }

  private List<ProjectMatch> run(final OpenApiDocument document) {
    return rule.check(document).stream().distinct().map(OpenApiProjectCheck::match).toList();
  }

  private static ProjectMatch match(final OpenApiFinding finding) {
    return new ProjectMatch(finding.file(), new RuleMatch(finding.span(), finding.message()));
  }

  private static boolean isEntry(final SyntaxNode root) {
    return YamlSyntax.OBJECT.equals(root.kind())
        && root.children().stream()
            .anyMatch(
                entry ->
                    ENTRY_KEY.equals(entry.children().getFirst().fields().get(YamlSyntax.VALUE)));
  }
}
