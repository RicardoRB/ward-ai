package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleNotRun;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
    final Map<AnalysisLanguage, FrontendSupport> support = supportByLanguage(frontends);
    final UnitTrees trees = new UnitTrees(support);
    final RuleRunCollector collector = new RuleRunCollector();
    for (final RuleDefinition rule : rules) {
      runRule(rule, units, support, trees, collector);
    }
    return collector.result();
  }

  private static Map<AnalysisLanguage, FrontendSupport> supportByLanguage(
      final List<FrontendSupport> frontends) {
    return frontends.stream()
        .collect(Collectors.toMap(FrontendSupport::language, Function.identity()));
  }

  private void runRule(
      final RuleDefinition definition,
      final List<SourceUnit> units,
      final Map<AnalysisLanguage, FrontendSupport> support,
      final UnitTrees trees,
      final RuleRunCollector collector) {
    if (!provided(definition, support).contains(definition.capability())) {
      collector.notRun(new RuleNotRun(definition.id(), definition.capability()));
      return;
    }
    final Optional<CompiledRule> rule = compile(definition, collector);
    if (rule.isEmpty()) {
      return;
    }
    for (final SourceUnit unit : units) {
      if (unit.language() == definition.language()) {
        runOnUnit(rule.get(), unit, trees, collector);
      }
    }
  }

  private static Set<RuleCapability> provided(
      final RuleDefinition definition, final Map<AnalysisLanguage, FrontendSupport> support) {
    final FrontendSupport frontend = support.get(definition.language());
    return frontend == null ? Set.of() : frontend.capabilities();
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
      final CompiledRule rule,
      final SourceUnit unit,
      final UnitTrees trees,
      final RuleRunCollector collector) {
    final Optional<SyntaxNode> root = trees.parse(unit, collector);
    if (root.isEmpty()) {
      return;
    }
    try {
      for (final RuleMatch match : rule.matches(root.get())) {
        collector.finding(finding(rule.definition(), unit, match));
      }
    } catch (final AnalysisFailedException exception) {
      collector.recordError(
          new RuleExecutionError(rule.definition().id(), unit.path(), "the rule could not run"));
    }
  }

  private static AnalysisFinding finding(
      final RuleDefinition definition, final SourceUnit unit, final RuleMatch match) {
    final Span span = match.span();
    return new AnalysisFinding(
        definition.id(),
        definition.severity(),
        match.message(),
        unit.path(),
        span == null ? null : span.startLine(),
        span == null ? null : span.startColumn(),
        null);
  }
}
