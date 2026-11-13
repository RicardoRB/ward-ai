package dev.ward.domain.gate;

/**
 * A file exists at a revision but Git cannot read it. It is not a missing file: callers must treat
 * it as an execution error. The message names the path and the revision, never file contents.
 */
public class RevisionReadException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** Creates the exception for a file that Git could not read. */
  public RevisionReadException(final String message) {
    super(message);
  }
}
