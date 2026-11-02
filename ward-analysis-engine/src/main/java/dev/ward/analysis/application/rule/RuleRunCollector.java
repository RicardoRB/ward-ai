package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleNotRun;
import dev.ward.analysis.domain.rule.RuleRunResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Gathers the outcomes of one engine run and returns them in the stable order. */
final class RuleRunCollector {
  private static final Comparator<String> NULLS_FIRST =
      Comparator.nullsFirst(Comparator.naturalOrder());
  private static final Comparator<AnalysisFinding> FINDING_ORDER =
      Comparator.comparing(AnalysisFinding::file, NULLS_FIRST)
          .thenComparing(AnalysisFinding::line, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(AnalysisFinding::column, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(AnalysisFinding::ruleId)
          .thenComparing(AnalysisFinding::message);
  private static final Comparator<RuleExecutionError> ERROR_ORDER =
      Comparator.comparing(RuleExecutionError::path, NULLS_FIRST)
          .thenComparing(RuleExecutionError::ruleId, NULLS_FIRST)
          .thenComparing(RuleExecutionError::reason);

  private final List<AnalysisFinding> findings = new ArrayList<>();
  private final List<RuleExecutionError> errors = new ArrayList<>();
  private final List<RuleNotRun> notRun = new ArrayList<>();

  void finding(final AnalysisFinding finding) {
    findings.add(finding);
  }

  void recordError(final RuleExecutionError error) {
    errors.add(error);
  }

  void notRun(final RuleNotRun skipped) {
    notRun.add(skipped);
  }

  RuleRunResult result() {
    return new RuleRunResult(
        findings.stream().sorted(FINDING_ORDER).toList(),
        errors.stream().sorted(ERROR_ORDER).toList(),
        notRun.stream().sorted(Comparator.comparing(RuleNotRun::ruleId)).toList());
  }
}
