package dev.ward.application.format;

import java.util.Objects;

/**
 * What formatting decided for one file.
 *
 * @param path the path relative to the project root, with {@code /} separators
 * @param verdict what happened to the file
 * @param line the first line that differs, counted from one, or zero when no line applies
 * @param reason the message for the user, or empty when there is none
 */
public record FormatFileOutcome(String path, Verdict verdict, int line, String reason) {
  /** The result of formatting one file. */
  public enum Verdict {
    /** The file already has the layout and the formatting Ward wants. */
    UNCHANGED,
    /** The file needs formatting; nothing was written. */
    NEEDS_FORMATTING,
    /** The file was rewritten. */
    WRITTEN,
    /** The formatter or its safety checks refused the file; nothing was written. */
    REFUSED,
    /** The file could not be read or written. */
    FAILED
  }

  /**
   * Checks the components.
   *
   * @throws NullPointerException if the path, verdict or reason is missing
   */
  public FormatFileOutcome {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(verdict, "verdict");
    Objects.requireNonNull(reason, "reason");
  }
}
