package dev.ward.analysis.domain.rule;

/** A rule catalog that cannot be used, such as one with duplicate rule ids. */
public class RuleConfigurationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception with a message for the user. */
  public RuleConfigurationException(final String message) {
    super(message);
  }

  /** Creates the exception with a message for the user and the problem that caused it. */
  public RuleConfigurationException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
