package dev.ward.analysis.domain.ir;

import java.util.List;
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
 * @param path the steps of the finding's path in order, all in {@code file}; empty when the finding
 *     has no path
 */
public record AnalysisFinding(
    String ruleId,
    AnalysisSeverity severity,
    String message,
    String file,
    Integer line,
    Integer column,
    String helpUri,
    List<FlowStep> path) {

  /**
   * Validates the required fields and the location, and copies the path.
   *
   * @throws NullPointerException if the rule, severity, message or path is missing
   * @throws IllegalArgumentException if a location part is not positive
   */
  public AnalysisFinding {
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(message, "message");
    path = List.copyOf(Objects.requireNonNull(path, "path"));
    if (line != null && line < 1 || column != null && column < 1) {
      throw new IllegalArgumentException("line and column are 1-based");
    }
  }
}
