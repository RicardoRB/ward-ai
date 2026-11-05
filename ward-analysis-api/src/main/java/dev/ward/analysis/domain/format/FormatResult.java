package dev.ward.analysis.domain.format;

import java.util.Objects;

/** The outcome of formatting one source text. */
public sealed interface FormatResult
    permits FormatResult.Formatted, FormatResult.Unchanged, FormatResult.Refused {

  /**
   * The formatted text, which differs from the input.
   *
   * @param text the new source text
   */
  record Formatted(String text) implements FormatResult {

    /**
     * Checks the text.
     *
     * @throws NullPointerException if the text is missing
     */
    public Formatted {
      Objects.requireNonNull(text, "text");
    }
  }

  /** The input is already formatted; nothing is written. */
  record Unchanged() implements FormatResult {}

  /**
   * The formatter or the safety checks refused the input; nothing is written.
   *
   * @param reason why the file was refused
   * @param line the first line that differs, or 1 when no line applies
   */
  record Refused(String reason, int line) implements FormatResult {

    /**
     * Checks the reason.
     *
     * @throws NullPointerException if the reason is missing
     */
    public Refused {
      Objects.requireNonNull(reason, "reason");
    }
  }
}
