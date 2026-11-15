package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.FlowView;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gives a rule that follows calls into other files the graphs of the files its unit depends on,
 * directly or through others. A file is parsed and lowered once per run. A file that does not parse
 * or lower has no graphs, and no error is recorded for it, as it is not the file being checked.
 */
final class RelatedFlows {
  private final Map<AnalysisLanguage, FrontendSupport> support;
  private final DependencySnapshot snapshot;
  private final Map<SourceUnit, List<ControlFlowGraph>> lowered = new IdentityHashMap<>();

  RelatedFlows(
      final Map<AnalysisLanguage, FrontendSupport> support, final DependencySnapshot snapshot) {
    this.support = support;
    this.snapshot = snapshot;
  }

  /** Returns {@code own}, with the graphs of the related files when the rule reads their bodies. */
  FlowView with(final CompiledRule rule, final SourceUnit unit, final FlowView own) {
    if (!rule.definition().capability().readsDependencyBodies()) {
      return own;
    }
    final List<ControlFlowGraph> related =
        snapshot.closureOf(unit.path()).stream()
            .flatMap(other -> lowered.computeIfAbsent(other, this::lower).stream())
            .toList();
    return new FlowView(own.graphs(), related);
  }

  private List<ControlFlowGraph> lower(final SourceUnit unit) {
    final FrontendSupport language = support.get(unit.language());
    if (language == null) {
      return List.of();
    }
    try {
      final ParseResult parsed = language.frontend().parse(unit);
      if (!parsed.diagnostics().isEmpty()) {
        return List.of();
      }
      return language.lowering().lower(unit, parsed.tree()).graphs();
    } catch (final AnalysisFailedException exception) {
      return List.of();
    }
  }
}
