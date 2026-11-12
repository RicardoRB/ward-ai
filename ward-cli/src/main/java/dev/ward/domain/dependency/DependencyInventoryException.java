package dev.ward.domain.dependency;

import dev.ward.domain.config.WardException;

/**
 * The inventory of a project could not be produced. The message names the project, the file or
 * command, the reason and the fix; it never holds an argument vector, an environment or captured
 * process output.
 */
public class DependencyInventoryException extends WardException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates the failure.
   *
   * @param project the project root
   * @param subject the file or the command (executable only) that failed
   * @param reason why it failed
   * @param fix the command or step that fixes it
   */
  public DependencyInventoryException(
      final String project, final String subject, final String reason, final String fix) {
    this(project, subject, reason, fix, null);
  }

  /**
   * Creates the failure with its cause.
   *
   * @param project the project root
   * @param subject the file or the command (executable only) that failed
   * @param reason why it failed
   * @param fix the command or step that fixes it
   * @param cause what went wrong underneath; its text is not part of the message
   */
  public DependencyInventoryException(
      final String project,
      final String subject,
      final String reason,
      final String fix,
      final Throwable cause) {
    super(
        "Cannot read the dependencies of "
            + project
            + ": "
            + subject
            + ": "
            + reason
            + ". Fix: "
            + fix,
        cause);
  }
}
