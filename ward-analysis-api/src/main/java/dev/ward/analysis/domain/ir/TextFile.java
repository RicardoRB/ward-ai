package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * One text file handed to a rule that reads text, with no language and no syntax tree.
 *
 * @param path project-root-relative path with {@code /} separators
 * @param text the full text of the file
 * @param lower the text with the letters {@code A-Z} lower-cased and nothing else changed, so an
 *     offset in it is an offset in {@code text}
 */
public record TextFile(String path, String text, String lower) {

  private static final int UPPER_TO_LOWER = 'a' - 'A';

  /**
   * Validates the required fields and the path separator.
   *
   * @throws IllegalArgumentException if the path contains a backslash, or the two texts differ in
   *     length
   */
  public TextFile {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(lower, "lower");
    if (path.indexOf('\\') >= 0) {
      throw new IllegalArgumentException("path must use / separators");
    }
    if (lower.length() != text.length()) {
      throw new IllegalArgumentException("lower must have the length of text");
    }
  }

  /** Creates the file and lower-cases its text once. */
  public TextFile(final String path, final String text) {
    this(path, text, asciiLower(Objects.requireNonNull(text, "text")));
  }

  private static String asciiLower(final String text) {
    final char[] chars = text.toCharArray();
    for (int index = 0; index < chars.length; index++) {
      if (chars[index] >= 'A' && chars[index] <= 'Z') {
        chars[index] += UPPER_TO_LOWER;
      }
    }
    return new String(chars);
  }
}
