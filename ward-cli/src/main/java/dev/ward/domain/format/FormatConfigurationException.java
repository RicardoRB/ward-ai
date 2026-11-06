package dev.ward.domain.format;

import dev.ward.domain.config.WardException;

/** A formatting configuration file sets an option Ward cannot read. The message names the file. */
public final class FormatConfigurationException extends WardException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception with a message for the user.
   *
   * @param message the message, naming the file, section and key
   */
  public FormatConfigurationException(final String message) {
    super(message);
  }

  /**
   * Creates the exception with a message for the user and its cause.
   *
   * @param message the message, naming the file
   * @param cause the read failure
   */
  public FormatConfigurationException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
