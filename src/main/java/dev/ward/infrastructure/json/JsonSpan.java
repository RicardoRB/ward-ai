package dev.ward.infrastructure.json;

/**
 * The text span of a JSON object or value: the index of its first and last character. An absent
 * span has {@code open} at -1.
 *
 * @param open the index of the first character
 * @param close the index of the last character
 */
public record JsonSpan(int open, int close) {
  /** The span of a member that is not in the text. */
  static final JsonSpan ABSENT = new JsonSpan(-1, -1);

  /** Returns whether the text holds this span. */
  boolean present() {
    return open >= 0;
  }
}
