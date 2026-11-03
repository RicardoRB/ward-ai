package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.RuleConfigurationException;

/** Builds the configuration errors that name the rule file they come from. */
final class RuleFileProblems {

  private RuleFileProblems() {}

  static RuleConfigurationException problem(final String resource, final String detail) {
    return new RuleConfigurationException(message(resource, detail));
  }

  static RuleConfigurationException problem(
      final String resource, final String detail, final Throwable cause) {
    return new RuleConfigurationException(message(resource, detail), cause);
  }

  private static String message(final String resource, final String detail) {
    return "Analysis rule file '"
        + resource
        + "' cannot be used: "
        + detail
        + ". Its rules are skipped.";
  }
}
