package dev.ward.domain.buildpolicy;

/** A build file that cannot be read or does not parse; an execution error that names the file. */
public final class BuildFileException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** Creates the error for {@code file} with {@code detail}. */
  public BuildFileException(final String file, final String detail) {
    super(file + ": " + detail);
  }

  /** Creates the error for {@code file} with {@code detail} and its {@code cause}. */
  public BuildFileException(final String file, final String detail, final Throwable cause) {
    super(file + ": " + detail, cause);
  }
}
