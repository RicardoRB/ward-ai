package dev.ward.analysis.domain.rule;

import java.util.Objects;

/**
 * A rule that the engine did not run because its frontend lacks a capability.
 *
 * @param ruleId the rule's id
 * @param missing the capability the rule requires and the frontend does not provide
 */
public record RuleNotRun(String ruleId, RuleCapability missing) {

  /**
   * Validates the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public RuleNotRun {
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(missing, "missing");
  }
}
