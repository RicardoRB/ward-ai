package dev.ward.domain.format;

import java.util.Objects;

/**
 * The text of one source file.
 *
 * @param text the text, without its byte-order mark
 * @param byteOrderMark whether the file starts with a UTF-8 byte-order mark
 * @param symbolicLink whether the path is a symbolic link, which Ward never rewrites
 */
public record SourceText(String text, boolean byteOrderMark, boolean symbolicLink) {
  /**
   * Checks the text.
   *
   * @throws NullPointerException if the text is missing
   */
  public SourceText {
    Objects.requireNonNull(text, "text");
  }
}
