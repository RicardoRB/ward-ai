package dev.ward.domain.gate;

import java.util.List;
import java.util.Objects;

/**
 * One violation reported by a quality-gate tool.
 *
 * <p>{@code file} is project-root-relative with {@code /} separators. Location parts and {@code
 * helpUri} are {@code null} when the tool does not report them. {@code path} is empty for a finding
 * that has no path.
 */
public record Finding(
    String gateId,
    String tool,
    String ruleId,
    FindingSeverity severity,
    String message,
    String file,
    Integer line,
    Integer column,
    String helpUri,
    List<FindingStep> path) {

  /**
   * Validates the required fields and copies the path.
   *
   * @throws NullPointerException if the gate, tool, rule, severity, message, or path is missing
   * @throws IllegalArgumentException if a location part is not positive
   */
  public Finding {
    Objects.requireNonNull(gateId, "gateId");
    Objects.requireNonNull(tool, "tool");
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(message, "message");
    path = List.copyOf(Objects.requireNonNull(path, "path"));
    if (line != null && line < 1 || column != null && column < 1) {
      throw new IllegalArgumentException("line and column are 1-based");
    }
  }

  /** Returns whether the finding points at a file. */
  public boolean located() {
    return file != null;
  }
}
