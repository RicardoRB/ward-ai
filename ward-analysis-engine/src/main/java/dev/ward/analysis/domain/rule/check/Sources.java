package dev.ward.analysis.domain.rule.check;

import java.util.Locale;
import java.util.regex.Pattern;

/** Which source files a check looks at: production code, test code or both. */
public enum Sources {
  /** Every file. */
  ALL,
  /** Files that are not test sources. */
  MAIN,
  /** Test sources only. */
  TEST;

  private static final Pattern TEST_PATH =
      Pattern.compile(
          "(^|/)(test|tests|__tests__|spec|Tests)/|(Test|Tests|Spec)\\.(java|kt|swift)$"
              + "|(^|/)test_[^/]*\\.py$|_test\\.py$|\\.(test|spec)\\.[jt]sx?$");

  /**
   * Reads the value of a rule's {@code sources} parameter.
   *
   * @throws IllegalArgumentException if the value is not {@code all}, {@code main} or {@code test}
   */
  public static Sources parse(final String value) {
    try {
      return valueOf(value.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException exception) {
      throw new IllegalArgumentException("sources must be all, main or test", exception);
    }
  }

  /** Tells whether a file at {@code path} is looked at. */
  boolean admits(final String path) {
    return this == ALL || this == TEST == TEST_PATH.matcher(path).find();
  }
}
