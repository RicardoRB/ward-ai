package dev.ward.analysis.domain.format;

import java.util.Objects;

/**
 * One non-whitespace token, comments included.
 *
 * @param type the token type, such as an identifier or a comment
 * @param text the token text exactly as written
 * @param line the line where the token starts, from 1
 */
public record FormatToken(String type, String text, int line) {

  /**
   * Checks the components.
   *
   * @throws NullPointerException if the type or the text is missing
   */
  public FormatToken {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(text, "text");
  }
}
