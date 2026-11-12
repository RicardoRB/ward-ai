package dev.ward.analysis.domain.api;

/** A source file whose API cannot be read. */
public final class ApiSurfaceException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final String path;

  /** Creates the exception for the file at {@code path}. */
  public ApiSurfaceException(final String path, final String message) {
    super(message);
    this.path = path;
  }

  /** Returns the project-relative path of the file. */
  public String path() {
    return path;
  }
}
