package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;

/** Turns a rule's match in a unit into the finding that the run reports. */
final class RuleFindings {

  private RuleFindings() {}

  /** Returns the finding of {@code match}, which {@code definition} reported in {@code unit}. */
  static AnalysisFinding of(
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
