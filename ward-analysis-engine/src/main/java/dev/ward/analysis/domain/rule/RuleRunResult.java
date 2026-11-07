package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import java.util.List;

/**
 * Everything one engine run produced, each list in the engine's stable order.
 *
 * @param findings the findings, sorted by file, line, column and rule id
 * @param executionErrors the units and rules that could not be analyzed
 * @param notRun the rules that were skipped for a missing capability
 */
public record RuleRunResult(
    List<AnalysisFinding> findings,
    List<RuleExecutionError> executionErrors,
    List<RuleNotRun> notRun) {

  /** Copies the lists. */
  public RuleRunResult {
    findings = List.copyOf(findings);
    executionErrors = List.copyOf(executionErrors);
    notRun = List.copyOf(notRun);
  }
}
