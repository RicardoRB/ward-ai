package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * One violation reported by the analysis engine.
 *
 * <p>Location parts and {@code helpUri} are {@code null} when a rule does not report them.
 *
 * @param ruleId the rule that reported the violation
 * @param severity how serious the violation is
 * @param message the human-readable message
 * @param file project-root-relative path with {@code /} separators, or {@code null}
 * @param line 1-based line, or {@code null}
 * @param column 1-based column, or {@code null}
 * @param helpUri link to the rule's documentation, or {@code null}
 */
public record AnalysisFinding(
    String ruleId,
    AnalysisSeverity severity,
    String message,
    String file,
    Integer line,
    Integer column,
    String helpUri) {

  /**
   * Validates the required fields and the location.
   *
   * @throws NullPointerException if the rule, severity or message is missing
   * @throws IllegalArgumentException if a location part is not positive
   */
  public AnalysisFinding {
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(message, "message");
    if (line != null && line < 1 || column != null && column < 1) {
      throw new IllegalArgumentException("line and column are 1-based");
    }
  }
}
