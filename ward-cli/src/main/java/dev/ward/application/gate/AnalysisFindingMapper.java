package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.FindingStep;
import java.util.Comparator;
import java.util.List;

/** Maps engine findings to the gate finding record, in a stable report order. */
public final class AnalysisFindingMapper {

  private static final Comparator<Finding> REPORT_ORDER =
      Comparator.comparing(Finding::file, Comparator.nullsLast(Comparator.<String>naturalOrder()))
          .thenComparing(Finding::line, Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
          .thenComparing(Finding::column, Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
          .thenComparing(Finding::ruleId)
          .thenComparing(Finding::message);

  /**
   * Maps the findings, carrying the caller's gate id and tool name.
   *
   * @param gateId the gate that produced the findings
   * @param tool the tool that produced the findings
   * @param findings the engine findings
   * @return the mapped findings, sorted by file, line, column, rule id and message
   */
  public List<Finding> map(
      final String gateId, final String tool, final List<AnalysisFinding> findings) {
    return findings.stream()
        .map(finding -> toFinding(gateId, tool, finding))
        .sorted(REPORT_ORDER)
        .toList();
  }

  /**
   * Maps one engine finding to a gate finding, with its taint path when it has one.
   *
   * @param gateId the gate that produced the finding
   * @param tool the tool that produced the finding
   * @param finding the engine finding
   * @return the gate finding
   */
  public static Finding toFinding(
      final String gateId, final String tool, final AnalysisFinding finding) {
    return new Finding(
        gateId,
        tool,
        finding.ruleId(),
        severityOf(finding.severity()),
        finding.message(),
        finding.file(),
        finding.line(),
        finding.column(),
        finding.helpUri(),
        pathOf(finding));
  }

  private static List<FindingStep> pathOf(final AnalysisFinding finding) {
    return finding.path().stream()
        .map(
            step ->
                new FindingStep(
                    step.file() == null ? finding.file() : step.file(),
                    step.span().startLine(),
                    step.span().startColumn(),
                    step.message()))
        .toList();
  }

  private static FindingSeverity severityOf(final AnalysisSeverity severity) {
    return switch (severity) {
      case ERROR -> FindingSeverity.ERROR;
      case WARNING -> FindingSeverity.WARNING;
      case NOTE -> FindingSeverity.NOTE;
    };
  }
}
