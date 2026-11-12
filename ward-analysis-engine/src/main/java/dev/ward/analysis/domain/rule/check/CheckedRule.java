package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.rule.DependencyView;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.Measured;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.rule.TextCompiledRule;
import dev.ward.analysis.domain.rule.UnitViews;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;

/** A rule whose matches come from one {@link RuleCheck}. */
public final class CheckedRule implements TextCompiledRule {

  private final RuleDefinition definition;
  private final RuleCheck check;

  /**
   * Binds a definition to its check.
   *
   * @throws NullPointerException if an argument is missing
   */
  public CheckedRule(final RuleDefinition definition, final RuleCheck check) {
    this.definition = Objects.requireNonNull(definition, "definition");
    this.check = Objects.requireNonNull(check, "check");
  }

  @Override
  public RuleDefinition definition() {
    return definition;
  }

  @Override
  public List<Measured> measures(final SyntaxNode root) {
    return check.measures(root);
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return check.matches(root);
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final DependencyView dependencies, final FlowView flow) {
    return check.matches(root, flow);
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final DependencyView dependencies,
      final FlowView flow,
      final SourceUnit unit) {
    return check.matches(root, flow, unit);
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final DependencyView dependencies,
      final FlowView flow,
      final ScopeView scopes,
      final SourceUnit unit) {
    return check.matches(root, flow, scopes, unit);
  }

  @Override
  public FactNeed factNeed() {
    return check.needs();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final DependencyView dependencies,
      final UnitViews views,
      final SourceUnit unit) {
    return check.matches(root, views.flow(), views.scopes(), views.types(), unit);
  }

  @Override
  public boolean isProject() {
    return check instanceof ProjectCheck;
  }

  @Override
  public List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    return check instanceof ProjectCheck project ? project.project(trees) : List.of();
  }

  @Override
  public List<ProjectMatch> project(
      final SortedMap<String, SyntaxNode> trees, final ProjectFiles files) {
    return check instanceof ProjectCheck project ? project.project(trees, files) : List.of();
  }

  @Override
  public List<RuleMatch> textMatches(final TextFile file) {
    return check instanceof TextCheck text ? text.textMatches(file) : List.of();
  }
}
