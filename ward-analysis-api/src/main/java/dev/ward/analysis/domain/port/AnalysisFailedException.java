package dev.ward.analysis.domain.port;

/**
 * Reports that one source unit or one rule could not be analyzed.
 *
 * <p>Frontends and rule matchers throw this instead of other runtime exceptions. The analysis
 * engine records it as an execution error for that unit or rule and continues with the rest. The
 * message is for developers; reports do not show it, so it must not contain source text.
 */
public final class AnalysisFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception with a developer-facing message. */
  public AnalysisFailedException(final String message) {
    super(message);
  }
}
