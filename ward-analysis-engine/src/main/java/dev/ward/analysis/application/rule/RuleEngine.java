package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.DependencyView;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleNotRun;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    final List<Prepared> prepared =
        rules.stream().map(rule -> prepare(rule, support, collector)).toList();
    final RunState state =
        new RunState(support, new UnitTrees(support, needs(prepared)), collector, snapshot);
    for (final Prepared rule : prepared) {
      runRule(rule, units, state);
    }
    return collector.result();
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

  /** A rule that can run, with the languages it runs on; none when it was not run or failed. */
  private record Prepared(Optional<CompiledRule> rule, List<AnalysisLanguage> runnable) {}

  /** Compiles each runnable rule once, up front, so the type needs of all rules are known. */
  private Prepared prepare(
      final RuleDefinition definition,
      final Map<AnalysisLanguage, FrontendSupport> support,
      final RuleRunCollector collector) {
    final List<AnalysisLanguage> runnable = runnable(definition, support);
    if (!runnable.containsAll(definition.languages())) {
      collector.notRun(new RuleNotRun(definition.id(), definition.capability()));
      return new Prepared(Optional.empty(), List.of());
    }
    return new Prepared(compile(definition, collector), runnable);
  }

  private static Map<AnalysisLanguage, List<FactNeed>> needs(final List<Prepared> prepared) {
    final Map<AnalysisLanguage, List<FactNeed>> needs = new HashMap<>();
    for (final Prepared item : prepared) {
      item.rule()
          .filter(rule -> rule.definition().capability() == RuleCapability.TYPES)
          .ifPresent(
              rule ->
                  rule.definition()
                      .languages()
                      .forEach(
                          language ->
                              needs
                                  .computeIfAbsent(language, key -> new ArrayList<>())
                                  .add(rule.factNeed())));
    }
    return needs;
  }

  private void runRule(
      final Prepared prepared, final List<SourceUnit> units, final RunState state) {
    if (prepared.rule().isEmpty()) {
      return;
    }
    for (final SourceUnit unit : units) {
      if (prepared.runnable().contains(unit.language())) {
        runOnUnit(prepared.rule().get(), unit, state);
      }
    }
  }

  /** The languages of {@code definition} whose frontend provides the rule's capability. */
  private static List<AnalysisLanguage> runnable(
      final RuleDefinition definition, final Map<AnalysisLanguage, FrontendSupport> support) {
    return definition.languages().stream()
        .filter(language -> provides(support.get(language), definition.capability()))
        .toList();
  }

  private static boolean provides(final FrontendSupport frontend, final RuleCapability capability) {
    return frontend != null && frontend.capabilities().contains(capability);
  }

  private Optional<CompiledRule> compile(
      final RuleDefinition definition, final RuleRunCollector collector) {
    try {
      return Optional.of(matcher.compile(definition));
    } catch (final AnalysisFailedException exception) {
      collector.recordError(
          new RuleExecutionError(definition.id(), null, "the rule could not be compiled"));
      return Optional.empty();
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
      rule.matches(
              root.get(),
              dependencies,
              state.trees().flows(rule, unit, root.get(), collector),
              state.trees().scopes(rule, unit, root.get(), dependencies, collector),
              state.trees().types(rule, unit, root.get(), collector),
              unit)
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
