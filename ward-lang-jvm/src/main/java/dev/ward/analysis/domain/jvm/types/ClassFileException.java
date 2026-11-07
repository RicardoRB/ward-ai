package dev.ward.analysis.domain.jvm.types;

/** A class file that is not well formed or uses a feature the reader does not know. */
public final class ClassFileException extends Exception {
  private static final long serialVersionUID = 1L;

  /** Creates the exception with {@code message}. */
  public ClassFileException(final String message) {
    super(message);
  }

  /** Creates the exception with {@code message} and the {@code cause} that was found. */
  public ClassFileException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
