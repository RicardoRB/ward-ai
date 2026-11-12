package dev.ward.domain.dependency;

/**
 * A lockfile whose text a reader cannot use: it does not parse, has a format version the reader
 * does not know, or holds a shape the reader does not recognize. The source turns it into a {@link
 * DependencyInventoryException} that names the file and the fix.
 */
public class LockfileFormatException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** Creates the failure with the reason, such as {@code unsupported lockfileVersion 4}. */
  public LockfileFormatException(final String reason) {
    super(reason);
  }

  /** Creates the failure with the reason and what went wrong underneath. */
  public LockfileFormatException(final String reason, final Throwable cause) {
    super(reason, cause);
  }
}
