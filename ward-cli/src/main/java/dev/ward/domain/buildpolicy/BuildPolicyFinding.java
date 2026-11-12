package dev.ward.domain.buildpolicy;

/**
 * One violation of a build rule.
 *
 * @param rule the rule
 * @param message what is wrong
 * @param file the project-relative file, or {@code null} when there is none
 * @param line the 1-based line, or {@code 0} when there is none
 */
public record BuildPolicyFinding(BuildRule rule, String message, String file, int line) {}
