package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.rule.RuleConfigurationException;

/** Builds the configuration errors that name the rule file they come from. */
public final class RuleFileProblems {

  private RuleFileProblems() {}

  /** Returns the error for a rule file that cannot be used, naming the file. */
  public static RuleConfigurationException problem(final String resource, final String detail) {
    return new RuleConfigurationException(message(resource, detail));
  }

  /** Returns the error for a rule file that cannot be used, with its cause. */
  public static RuleConfigurationException problem(
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
