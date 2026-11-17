package dev.ward.domain.secrets;

import java.util.Objects;

/**
 * Where a rule found a secret in a prompt. It never holds the secret.
 *
 * @param file the project-relative path of the attached file, or {@code null} for the message
 * @param ruleId the id of the rule that matched
 * @param line the 1-based line
 * @param column the 1-based column
 */
public record PromptFinding(String file, String ruleId, int line, int column) {
  /** Validates the rule id. */
  public PromptFinding {
    Objects.requireNonNull(ruleId, "ruleId");
  }

  /** Whether the finding is in the message rather than in an attached file. */
  public boolean inMessage() {
    return file == null;
  }
}
