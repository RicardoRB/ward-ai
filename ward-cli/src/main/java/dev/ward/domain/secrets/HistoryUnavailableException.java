package dev.ward.domain.secrets;

/** The Git history could not be read. The message holds no source text. */
public final class HistoryUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception with the reason. */
  public HistoryUnavailableException(final String message) {
    super(message);
  }
}
