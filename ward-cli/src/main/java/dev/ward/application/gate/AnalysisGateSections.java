package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.domain.gate.GateSection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Splits the findings of a {@code ward} gate run by the category of the rule that reported them.
 */
final class AnalysisGateSections {
  private static final String NO_CATEGORY = "other";

  private AnalysisGateSections() {}

  /**
   * One section per category of {@code rules}, in name order, with the findings its rules reported.
   * A category whose rules found nothing is still listed, so the report shows it was checked.
   */
  static List<GateSection> of(
      final List<AnalysisFinding> findings, final List<RuleDefinition> rules) {
    final Map<String, String> categories =
        rules.stream()
            .collect(
                Collectors.toMap(
                    RuleDefinition::id, AnalysisGateSections::category, (first, second) -> first));
    final Map<String, List<AnalysisFinding>> byCategory =
        findings.stream()
            .collect(
                Collectors.groupingBy(
                    finding -> categories.getOrDefault(finding.ruleId(), NO_CATEGORY)));
    return Stream.concat(categories.values().stream(), byCategory.keySet().stream())
        .distinct()
        .sorted()
        .map(name -> section(name, byCategory.getOrDefault(name, List.of())))
        .toList();
  }

  private static GateSection section(final String name, final List<AnalysisFinding> findings) {
    return new GateSection(
        name,
        countOf(findings, AnalysisSeverity.ERROR),
        countOf(findings, AnalysisSeverity.WARNING));
  }

  private static String category(final RuleDefinition rule) {
    return rule.category().isEmpty() ? NO_CATEGORY : rule.category();
  }

  private static int countOf(
      final List<AnalysisFinding> findings, final AnalysisSeverity severity) {
    return Math.toIntExact(
        findings.stream().filter(finding -> finding.severity() == severity).count());
  }
}
