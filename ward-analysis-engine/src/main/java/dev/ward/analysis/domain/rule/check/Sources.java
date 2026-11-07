package dev.ward.analysis.domain.rule.check;

import java.util.Locale;
import java.util.regex.Pattern;

/** Which source files a check looks at: production code, test code, application code or all. */
public enum Sources {
  /** Every file. */
  ALL,
  /** Files that are not test sources. */
  MAIN,
  /** Test sources only. */
  TEST,
  /**
   * Files that are neither test sources nor tool configuration such as {@code vitest.config.ts}.
   */
  APP;

  private static final Pattern TEST_PATH =
      Pattern.compile(
          "(^|/)(test|tests|__tests__|spec|Tests)/|(Test|Tests|Spec)\\.(java|kt|swift)$"
              + "|(^|/)test_[^/]*\\.py$|_test\\.py$|\\.(test|spec)\\.[jt]sx?$");

  private static final Pattern TOOL_CONFIG = Pattern.compile("(^|/)[^/]+\\.config\\.[cm]?[jt]s$");

  /**
   * Reads the value of a rule's {@code sources} parameter.
   *
   * @throws IllegalArgumentException if the value is not {@code all}, {@code main}, {@code test} or
   *     {@code app}
   */
  public static Sources parse(final String value) {
    try {
      return valueOf(value.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException exception) {
      throw new IllegalArgumentException("sources must be all, main, test or app", exception);
    }
  }

  /** Tells whether a file at {@code path} is looked at. */
  boolean admits(final String path) {
    return switch (this) {
      case ALL -> true;
      case MAIN -> !TEST_PATH.matcher(path).find();
      case TEST -> TEST_PATH.matcher(path).find();
      case APP -> !TEST_PATH.matcher(path).find() && !TOOL_CONFIG.matcher(path).find();
    };
  }
}
