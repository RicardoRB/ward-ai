package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Parses each source unit once per run, however many rules run over it. */
final class UnitTrees {
  private final Map<AnalysisLanguage, FrontendSupport> support;
  private final Map<SourceUnit, Optional<SyntaxNode>> parsed = new HashMap<>();

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
}
