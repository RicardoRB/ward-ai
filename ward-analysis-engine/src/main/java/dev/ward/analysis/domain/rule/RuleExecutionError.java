package dev.ward.analysis.domain.rule;

/**
 * A unit or rule that could not be analyzed. It is an error, never a finding or a pass.
 *
 * @param ruleId the rule that failed, or {@code null} when a unit failed for every rule
 * @param path the project-root-relative path of the unit, or {@code null} when a rule failed before
 *     any unit
 * @param reason a short description that contains no source text
 */
public record RuleExecutionError(String ruleId, String path, String reason) {}
