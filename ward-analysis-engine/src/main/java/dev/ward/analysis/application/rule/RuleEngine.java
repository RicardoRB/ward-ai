package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.WorkLimit;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.stream.Collectors;

/**
 * Runs analysis rules over source units. A rule runs only where its language's frontend provides
 * the rule's capability; a unit or rule that fails is recorded as an execution error, and the run
 * continues. Findings come back sorted, so the same input gives the same output. With a work limit
 * above one the units are analyzed several at once, each with every rule in rule order; the result
 * does not depend on the limit.
 */
public final class RuleEngine {
  private static final int SERIAL = 1;

  private final RuleMatcher matcher;
  private final WorkLimit limit;

  /** Creates an engine that builds its rules with {@code matcher} and analyzes a unit at a time. */
  public RuleEngine(final RuleMatcher matcher) {
    this(matcher, WorkLimit.serial());
  }

  /**
   * Creates an engine that builds its rules with {@code matcher} and analyzes as many units at once
   * as {@code limit} allows.
   */
  public RuleEngine(final RuleMatcher matcher, final WorkLimit limit) {
    this.matcher = matcher;
    this.limit = limit;
  }

  /**
   * Runs {@code rules} over {@code units}. A rule whose language has no frontend, or whose frontend
   * lacks the rule's capability, is reported as not run.
   */
  public RuleRunResult run(
      final List<SourceUnit> units,
      final List<FrontendSupport> frontends,
      final List<RuleDefinition> rules
  ) {
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
      final DependencySnapshot snapshot
  ) {
    final Map<AnalysisLanguage, FrontendSupport> support = supportByLanguage(frontends);
    final RuleRunCollector collector = new RuleRunCollector();
    final List<PreparedRule> prepared = PreparedRule.all(rules, support, matcher, collector);
    final UnitRuns runs = new UnitRuns(support, prepared, snapshot);
    analyzed(units, runs).forEach(collector::addAll);
    return collector.result();
  }

  /**
   * Analyzes the units on analysis threads. With several threads the work is split by unit. With
   * one thread it stays rule by rule over trees that live until the run ends, as before #585: file
   * by file, the collector of the native binary has to free the trees of each file from its old
   * generation, and a cold check of this repository took 472 s instead of 336 s
   * (docs/performance/cold-analysis.md).
   */
  private List<RuleRunCollector> analyzed(final List<SourceUnit> units, final UnitRuns runs) {
    if (limit.threads() == SERIAL) {
      return limit.map(List.of(units), runs::ruleByRule);
    }
    return limit.map(units, runs::fileByFile);
  }

  /** Returns the limit of the threads that analyze units. */
  public WorkLimit limit() {
    return limit;
  }

  /**
   * Runs the whole-project rules among {@code rules} once over all of {@code units}. A project rule
   * is a fact about several files, so it runs after the per-unit rules and finds nothing there.
   */
  public List<AnalysisFinding> runProject(
      final List<SourceUnit> units,
      final List<FrontendSupport> frontends,
      final List<RuleDefinition> rules
  ) {
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
      final ProjectFiles files
  ) {
    final Map<AnalysisLanguage, FrontendSupport> support = supportByLanguage(frontends);
    final RuleRunCollector collector = new RuleRunCollector();
    final UnitTrees trees = new UnitTrees(support, Map.of());
    final List<PreparedRule> prepared = PreparedRule.all(rules, support, matcher, collector);
    final List<AnalysisFinding> findings = new ArrayList<>();
    for (final PreparedRule rule : prepared) {
      rule.rule()
          .filter(CompiledRule::isProject)
          .filter(
              compiled ->
                  !ProjectCheckScope.coveredByAnother(compiled.definition(), prepared, support)
          )
          .ifPresent(
              compiled ->
                  findings.addAll(
                      projectFindings(
                          compiled,
                          ProjectCheckScope.parsedTrees(
                              ProjectCheckScope.reads(rule, compiled.definition(), support),
                              units,
                              trees,
                              collector
                          ),
                          files
                      )
                  )
          );
    }
    return List.copyOf(findings);
  }

  private static List<AnalysisFinding> projectFindings(
      final CompiledRule rule,
      final SortedMap<String,
      SyntaxNode> parsed,
      final ProjectFiles files
  ) {
    return rule.project(parsed, files).stream()
        .map(match -> RuleFindings.at(rule.definition(), match.path(), match.match()))
        .toList();
  }

  private static Map<AnalysisLanguage, FrontendSupport> supportByLanguage(
      final List<FrontendSupport> frontends
  ) {
    return frontends.stream()
        .collect(Collectors.toMap(FrontendSupport::language, frontend -> frontend));
  }
}
