package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.SortedMap;

/** A rule ready to run over syntax trees. A {@link RuleMatcher} builds it from a definition. */
public interface CompiledRule {

  /** Returns the definition this rule was built from. */
  RuleDefinition definition();

  /**
   * Returns the matches of this rule in the tree rooted at {@code root}.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the rule cannot run on the
   *     tree
   */
  List<RuleMatch> matches(SyntaxNode root);

  /**
   * Returns the value this rule measures for every declaration in the tree rooted at {@code root},
   * including those within its limit. A rule that measures nothing returns none, as this default
   * method does.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the rule cannot run on the
   *     tree
   */
  default List<Measured> measures(final SyntaxNode root) {
    return List.of();
  }

  /**
   * Returns the matches of this rule in the tree rooted at {@code root}, given the files around it.
   * A rule that reads no dependencies needs only {@link #matches(SyntaxNode)}, which this default
   * method delegates to.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the rule cannot run on the
   *     tree
   */
  default List<RuleMatch> matches(final SyntaxNode root, final DependencyView dependencies) {
    return matches(root);
  }

  /**
   * Returns the matches of this rule in the tree rooted at {@code root}, given the files around it
   * and the control-flow graphs of the unit. A rule that reads no graphs needs only {@link
   * #matches(SyntaxNode, DependencyView)}, which this default method delegates to.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the rule cannot run on the
   *     tree
   */
  default List<RuleMatch> matches(
      final SyntaxNode root, final DependencyView dependencies, final FlowView flow) {
    return matches(root, dependencies);
  }

  /**
   * Returns the matches of this rule in the tree rooted at {@code root} of the file {@code unit},
   * given the files around it and the control-flow graphs of the unit. A rule that reads neither
   * the path nor the text needs only {@link #matches(SyntaxNode, DependencyView, FlowView)}, which
   * this default method delegates to.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the rule cannot run on the
   *     tree
   */
  default List<RuleMatch> matches(
      final SyntaxNode root,
      final DependencyView dependencies,
      final FlowView flow,
      final SourceUnit unit) {
    return matches(root, dependencies, flow);
  }

  /**
   * Returns the matches of this rule in the tree rooted at {@code root} of the file {@code unit},
   * given the files around it, the control-flow graphs and the scopes of the unit. A rule that
   * reads no scopes needs only {@link #matches(SyntaxNode, DependencyView, FlowView, SourceUnit)},
   * which this default method delegates to.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the rule cannot run on the
   *     tree
   */
  default List<RuleMatch> matches(
      final SyntaxNode root,
      final DependencyView dependencies,
      final FlowView flow,
      final ScopeView scopes,
      final SourceUnit unit) {
    return matches(root, dependencies, flow, unit);
  }

  /** What this rule reads from the type facts; none unless it has capability {@code TYPES}. */
  default FactNeed factNeed() {
    return FactNeed.NONE;
  }

  /**
   * Returns the matches of this rule in the tree rooted at {@code root} of the file {@code unit},
   * given everything the other overloads take and the type facts of the unit. A rule that reads no
   * type facts needs only the overload without them, which this default method delegates to.
   *
   * @throws dev.ward.analysis.domain.port.AnalysisFailedException if the rule cannot run on the
   *     tree
   */
  default List<RuleMatch> matches(
      final SyntaxNode root,
      final DependencyView dependencies,
      final UnitViews views,
      final SourceUnit unit) {
    return matches(root, dependencies, views.flow(), views.scopes(), unit);
  }

  /** Tells whether this rule is a fact about the whole project, run by {@link #project}. */
  default boolean isProject() {
    return false;
  }

  /**
   * Returns the matches of this rule over every file of its language at once. Only a rule for which
   * {@link #isProject()} holds returns any.
   */
  default List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    return List.of();
  }

  /**
   * Returns the whole-project matches with the manifest files of the project. By default it ignores
   * the files and returns {@link #project(SortedMap)}.
   */
  default List<ProjectMatch> project(
      final SortedMap<String, SyntaxNode> trees, final ProjectFiles files) {
    return project(trees);
  }
}
