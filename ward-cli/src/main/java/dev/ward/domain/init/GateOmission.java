package dev.ward.domain.init;

/**
 * A selected gate init leaves out because no catalog version of its tool runs on the project's
 * runtime JDK.
 *
 * @param gate the gate id
 * @param reason why, naming the tool, the JDK it needs, and the JDK the project builds on
 */
public record GateOmission(String gate, String reason) {
  /**
   * Validates the omission.
   *
   * @throws IllegalArgumentException if a value is blank
   */
  public GateOmission {
    if (gate == null || gate.isBlank() || reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Gate and reason are required");
    }
  }

  /** Returns the plan note, such as {@code spotbugs omitted: <reason>}. */
  public String note() {
    return gate + " omitted: " + reason;
  }
}
