package dev.ward.application.gate;

/** The reports of a {@code crap} check cannot be used. The message names the file and the fix. */
final class CrapReportException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  CrapReportException(final String message) {
    super(message);
  }
}
