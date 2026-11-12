package dev.ward.domain.gate;

/** A coverage report cannot be read or parsed. The message names the report, never its content. */
public final class LineCoverageException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** Creates the exception with {@code message}. */
  public LineCoverageException(final String message) {
    super(message);
  }

  /** Creates the exception with {@code message}, caused by {@code cause}. */
  public LineCoverageException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
