package dev.ward.application.gate.apicompat;

/** An input of the {@code api-compat} gate that cannot be used; the gate ends in an error. */
final class ApiCompatException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  ApiCompatException(final String message) {
    super(message);
  }

  ApiCompatException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
