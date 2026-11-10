package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.DependencyView;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.domain.rule.UnitViews;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Runs analysis rules over source units. A rule runs only where its language's frontend provides
 * the rule's capability; a unit or rule that fails is recorded as an execution error, and the run
 * continues. Findings come back sorted, so the same input gives the same output.
 */
public final class RuleEngine {

  private final RuleMatcher matcher;

  /** Creates an engine that builds its rules with {@code matcher}. */
  public RuleEngine(final RuleMatcher matcher) {
    this.matcher = matcher;
  }

  /**
   * Runs {@code rules} over {@code units}. A rule whose language has no frontend, or whose frontend
   * lacks the rule's capability, is reported as not run.
   */
  public RuleRunResult run(
      final List<SourceUnit> units,
      final List<FrontendSupport> frontends,
      final List<RuleDefinition> rules) {
    return run(units, frontends, rules, DependencySnapshot.EMPTY);
  }

  /**
   * Runs {@code rules} over {@code units}, giving the rules that read capability {@code SYMBOLS}
   * the dependencies of each unit from {@code snapshot}.
   */
  public RuleRunResult run(
      final List<SourceUnit> units,
      final List<FrontendSupport> frontends,
      final List<RuleDefinition> rules,
      final DependencySnapshot snapshot) {
    final Map<AnalysisLanguage, FrontendSupport> support = supportByLanguage(frontends);
    final RuleRunCollector collector = new RuleRunCollector();
    final List<PreparedRule> prepared = PreparedRule.all(rules, support, matcher, collector);
    final RunState state =
        new RunState(
            support, new UnitTrees(support, PreparedRule.needs(prepared)), collector, snapshot);
    for (final PreparedRule rule : prepared) {
      runRule(rule, units, state);
    }
    return collector.result();
  }

  /**
   * Runs the whole-project rules among {@code rules} once over all of {@code units}. A project rule
   * is a fact about several files, so it runs after the per-unit rules and finds nothing there.
   */
  public List<AnalysisFinding> runProject(
      final List<SourceUnit> units,
      final List<FrontendSupport> frontends,
      final List<RuleDefinition> rules) {
    return runProject(units, frontends, rules, ProjectFiles.empty());
  }

  /**
   * Like {@link #runProject(List, List, List)}, and gives each project check the manifest files of
   * the project. A match at the path of a manifest file is a finding at that path, line and column.
   */
  public List<AnalysisFinding> runProject(
      final List<SourceUnit> units,
      final List<FrontendSupport> frontends,
      final List<RuleDefinition> rules,
      final ProjectFiles files) {
    final Map<AnalysisLanguage, FrontendSupport> support = supportByLanguage(frontends);
    final RuleRunCollector collector = new RuleRunCollector();
    final UnitTrees trees = new UnitTrees(support, Map.of());
    final List<AnalysisFinding> findings = new ArrayList<>();
    for (final PreparedRule prepared : PreparedRule.all(rules, support, matcher, collector)) {
      prepared
          .rule()
          .filter(CompiledRule::isProject)
          .ifPresent(
              rule ->
                  findings.addAll(
                      projectFindings(
                          rule, parsedTrees(prepared, units, trees, collector), files)));
    }
    return List.copyOf(findings);
  }

  /** The syntax trees of the units the rule's language runs on, by path, in path order. */
  private static SortedMap<String, SyntaxNode> parsedTrees(
      final PreparedRule prepared,
      final List<SourceUnit> units,
      final UnitTrees trees,
      final RuleRunCollector collector) {
    final SortedMap<String, SyntaxNode> parsed = new TreeMap<>();
    for (final SourceUnit unit : units) {
      if (prepared.runnable().contains(unit.language())) {
        trees.parse(unit, collector).ifPresent(root -> parsed.put(unit.path(), root));
      }
    }
    return parsed;
  }

  private static List<AnalysisFinding> projectFindings(
      final CompiledRule rule,
      final SortedMap<String, SyntaxNode> parsed,
      final ProjectFiles files) {
    return rule.project(parsed, files).stream()
        .map(match -> RuleFindings.at(rule.definition(), match.path(), match.match()))
        .toList();
  }

  /** What one run shares between its rules. */
  private record RunState(
      Map<AnalysisLanguage, FrontendSupport> support,
      UnitTrees trees,
      RuleRunCollector collector,
      DependencySnapshot snapshot) {}

  private static Map<AnalysisLanguage, FrontendSupport> supportByLanguage(
      final List<FrontendSupport> frontends) {
    return frontends.stream()
        .collect(Collectors.toMap(FrontendSupport::language, frontend -> frontend));
  }

  private void runRule(
      final PreparedRule prepared, final List<SourceUnit> units, final RunState state) {
    if (prepared.rule().isEmpty()) {
      return;
    }
    for (final SourceUnit unit : units) {
      if (prepared.runnable().contains(unit.language())) {
        runOnUnit(prepared.rule().get(), unit, state);
      }
    }
  }

  private static void runOnUnit(
      final CompiledRule rule, final SourceUnit unit, final RunState state) {
    final RuleRunCollector collector = state.collector();
    final Optional<SyntaxNode> root = state.trees().parse(unit, collector);
    if (root.isEmpty()) {
      return;
    }
    try {
      final DependencyView dependencies = view(rule, unit, state);
      final UnitViews views =
          new UnitViews(
              state.trees().flows(rule, unit, root.get(), collector),
              state.trees().scopes(rule, unit, root.get(), dependencies, collector),
              state.trees().types(rule, unit, root.get(), collector));
      rule.matches(root.get(), dependencies, views, unit)
          .forEach(match -> collector.finding(RuleFindings.of(rule.definition(), unit, match)));
    } catch (final AnalysisFailedException exception) {
      collector.recordError(
          new RuleExecutionError(rule.definition().id(), unit.path(), "the rule could not run"));
    }
  }

  /** Only a rule that reads {@code SYMBOLS} or {@code SCOPES} sees the files around the unit. */
  private static DependencyView view(
      final CompiledRule rule, final SourceUnit unit, final RunState state) {
    if (!rule.definition().capability().readsDependencies()) {
      return DependencyView.NONE;
    }
    return state.snapshot().view(unit.path());
  }
}
