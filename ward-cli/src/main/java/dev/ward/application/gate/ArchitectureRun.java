package dev.ward.application.gate;

import dev.ward.analysis.application.rule.DependencySnapshot;
import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.graph.ProjectFiles;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.ArchitectureRule;
import dev.ward.analysis.domain.rule.ArchitectureRuleBody;
import dev.ward.analysis.domain.rule.ArchitectureRuleCheck;
import dev.ward.analysis.domain.rule.DependencyArchitectureRule;
import dev.ward.analysis.domain.rule.LocationArchitectureRule;
import dev.ward.analysis.domain.rule.NamingArchitectureRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The architecture rules of one run over its files: the findings of every graph rule, and the
 * execution errors of the general rules of {@code ward-architecture.toml} that cannot be evaluated
 * (a rule that selects no file, a dependency rule over a language with no dependency edges).
 */
final class ArchitectureRun {
  private static final Set<String> GENERAL =
      Set.of(DependencyArchitectureRule.ID, NamingArchitectureRule.ID, LocationArchitectureRule.ID);

  private final List<RuleDefinition> rules;
  private final ProjectFiles project;

  private ArchitectureRun(final List<RuleDefinition> rules, final ProjectFiles project) {
    this.rules = List.copyOf(rules);
    this.project = project;
  }

  /** The findings of every graph rule among {@code rules}, over the files of the run. */
  static List<AnalysisFinding> findings(
      final List<RuleDefinition> rules,
      final DependencySnapshot snapshot,
      final List<SourceUnit> units) {
    return of(rules, snapshot, units).findings(snapshot.graph());
  }

  /** Returns {@code run} with an execution error for each general rule that cannot be checked. */
  static RuleRunResult withErrors(
      final RuleRunResult run,
      final List<RuleDefinition> rules,
      final DependencySnapshot snapshot,
      final List<SourceUnit> units) {
    return of(rules, snapshot, units).withErrors(run);
  }

  private static ArchitectureRun of(
      final List<RuleDefinition> rules,
      final DependencySnapshot snapshot,
      final List<SourceUnit> units) {
    final Map<String, AnalysisLanguage> sources = new LinkedHashMap<>();
    for (final SourceUnit unit : units) {
      if (unit.language() != AnalysisLanguage.OPENAPI) {
        sources.put(unit.path(), unit.language());
      }
    }
    return new ArchitectureRun(
        rules, new ProjectFiles(snapshot.summaries().keySet(), snapshot.entries(), sources));
  }

  /**
   * Returns the definitions of the general rules, one per rule family that has a rule; the rules
   * travel in the body.
   */
  static List<RuleDefinition> definitions(final List<ArchitectureRule> rules) {
    return Stream.of(
            DependencyArchitectureRule.ID, NamingArchitectureRule.ID, LocationArchitectureRule.ID)
        .map(id -> family(id, rules))
        .flatMap(Optional::stream)
        .toList();
  }

  private List<AnalysisFinding> findings(final DependencyGraph graph) {
    return ArchitectureGraphRules.findings(rules, graph, project);
  }

  private RuleRunResult withErrors(final RuleRunResult run) {
    final List<RuleExecutionError> errors = new ArrayList<>(run.executionErrors());
    rules.stream()
        .filter(definition -> GENERAL.contains(definition.id()))
        .forEach(
            definition ->
                errors.addAll(
                    ArchitectureRuleCheck.errors(
                        ArchitectureRuleBody.decode(definition.body()), project)));
    return new RuleRunResult(run.findings(), errors, run.notRun());
  }

  private static Optional<RuleDefinition> family(
      final String id, final List<ArchitectureRule> rules) {
    final List<ArchitectureRule> own =
        rules.stream().filter(rule -> rule.constraint().kind().ruleId().equals(id)).toList();
    return own.isEmpty()
        ? Optional.empty()
        : Optional.of(ArchitectureGraphRules.definition(id, ArchitectureRuleBody.encode(own)));
  }
}
