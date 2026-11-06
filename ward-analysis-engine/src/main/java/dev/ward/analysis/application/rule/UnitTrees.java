package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.DependencyView;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/** Parses each source unit once per run, however many rules run over it. */
final class UnitTrees {
  private final Map<AnalysisLanguage, FrontendSupport> support;
  private final Map<SourceUnit, Optional<SyntaxNode>> parsed = new HashMap<>();
  private final Map<SourceUnit, FlowView> lowered = new IdentityHashMap<>();
  private final Map<SourceUnit, UnitScopes> scoped = new IdentityHashMap<>();

  UnitTrees(final Map<AnalysisLanguage, FrontendSupport> support) {
    this.support = support;
  }

  Optional<SyntaxNode> parse(final SourceUnit unit, final RuleRunCollector collector) {
    return parsed.computeIfAbsent(unit, key -> parseOnce(key, collector));
  }

  private Optional<SyntaxNode> parseOnce(final SourceUnit unit, final RuleRunCollector collector) {
    try {
      final ParseResult result = support.get(unit.language()).frontend().parse(unit);
      if (!result.diagnostics().isEmpty()) {
        collector.recordError(
            new RuleExecutionError(null, unit.path(), "the unit has syntax errors"));
        return Optional.empty();
      }
      return Optional.of(result.tree());
    } catch (final AnalysisFailedException exception) {
      collector.recordError(
          new RuleExecutionError(null, unit.path(), "the unit could not be parsed"));
      return Optional.empty();
    }
  }

  /** The graphs a rule reads: none unless the rule reads control flow or data flow. */
  FlowView flows(
      final CompiledRule rule,
      final SourceUnit unit,
      final SyntaxNode root,
      final RuleRunCollector collector) {
    final RuleCapability capability = rule.definition().capability();
    if (capability != RuleCapability.CFG && capability != RuleCapability.DATAFLOW) {
      return FlowView.NONE;
    }
    final String ruleId = rule.definition().id();
    final FlowView known = lowered.get(unit);
    if (known != null) {
      return known;
    }
    final FlowView view = lower(unit, root, ruleId, collector);
    lowered.put(unit, view);
    return view;
  }

  private FlowView lower(
      final SourceUnit unit,
      final SyntaxNode root,
      final String ruleId,
      final RuleRunCollector collector) {
    try {
      final ControlFlowResult result = support.get(unit.language()).lowering().lower(unit, root);
      result
          .failures()
          .forEach(
              failure ->
                  collector.recordError(
                      new RuleExecutionError(ruleId, unit.path(), failure.message())));
      return new FlowView(result.graphs());
    } catch (final AnalysisFailedException exception) {
      collector.recordError(
          new RuleExecutionError(ruleId, unit.path(), "the control flow could not be lowered"));
      return FlowView.NONE;
    }
  }

  /**
   * The scopes a rule reads: none unless the rule reads capability {@code SCOPES}. A unit's scopes
   * are built once per run, however many rules read them.
   */
  ScopeView scopes(
      final CompiledRule rule,
      final SourceUnit unit,
      final SyntaxNode root,
      final DependencyView dependencies,
      final RuleRunCollector collector) {
    if (rule.definition().capability() != RuleCapability.SCOPES) {
      return ScopeView.NONE;
    }
    final ScopeAnalysis analysis = support.get(unit.language()).scopes();
    try {
      final UnitScopes built = scoped.computeIfAbsent(unit, key -> analysis.analyze(key, root));
      return ScopeView.of(built, dependencies, analysis);
    } catch (final AnalysisFailedException exception) {
      collector.recordError(
          new RuleExecutionError(
              rule.definition().id(), unit.path(), "the scopes could not be built"));
      return ScopeView.NONE;
    }
  }
}
