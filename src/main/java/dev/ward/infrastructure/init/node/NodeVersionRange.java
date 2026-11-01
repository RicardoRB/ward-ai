package dev.ward.infrastructure.init.node;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The subset of npm version ranges Ward's Node catalog uses: alternatives joined by {@code ||},
 * each a space-separated list of {@code ^}, {@code >=}, {@code <} or exact comparators.
 */
final class NodeVersionRange {
  private static final Pattern VERSION = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");
  private static final int PARTS = 3;

  private NodeVersionRange() {}

  /**
   * Returns the lowest {@code major.minor.patch} that {@code declared} names, such as {@code 5.9.3}
   * for {@code ~5.9.3}, or empty when it names none.
   */
  static Optional<String> lowerBound(final String declared) {
    final Matcher matcher = VERSION.matcher(declared);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(
        String.join(".", Arrays.stream(parse(matcher)).mapToObj(String::valueOf).toList()));
  }

  /** Returns whether {@code version}, a full {@code major.minor.patch}, satisfies {@code range}. */
  static boolean matches(final String range, final String version) {
    return minimum(range, version, false).isPresent();
  }

  /**
   * Returns the lower bound of the first alternative that a Node major version satisfies, comparing
   * majors only because detection knows only the major; empty when none does.
   */
  static Optional<String> nodeMinimum(final String range, final int major) {
    return minimum(range, String.valueOf(major), true);
  }

  private static Optional<String> minimum(
      final String range, final String version, final boolean majorOnly) {
    final int[] actual = parse(version);
    for (final String alternative : range.split("\\|\\|")) {
      final String trimmed = alternative.trim();
      if (Arrays.stream(trimmed.split("\\s+"))
          .allMatch(comparator -> satisfies(comparator, actual, majorOnly))) {
        return Optional.of(lowerBound(trimmed).orElse("0.0.0"));
      }
    }
    return Optional.empty();
  }

  private static boolean satisfies(
      final String comparator, final int[] actual, final boolean majorOnly) {
    if (comparator.startsWith(">=")) {
      return compare(actual, parse(comparator.substring(2)), majorOnly) >= 0;
    }
    if (comparator.startsWith("<")) {
      return compare(actual, parse(comparator.substring(1)), majorOnly) < 0;
    }
    if (comparator.startsWith("^")) {
      final int[] bound = parse(comparator.substring(1));
      return actual[0] == bound[0] && compare(actual, bound, majorOnly) >= 0;
    }
    return compare(actual, parse(comparator), majorOnly) == 0;
  }

  private static int compare(final int[] left, final int[] right, final boolean majorOnly) {
    final int parts = majorOnly ? 1 : PARTS;
    for (int i = 0; i < parts; i++) {
      final int order = Integer.compare(left[i], right[i]);
      if (order != 0) {
        return order;
      }
    }
    return 0;
  }

  private static int[] parse(final String version) {
    final Matcher matcher = VERSION.matcher(version);
    return matcher.find() ? parse(matcher) : new int[PARTS];
  }

  private static int[] parse(final Matcher matcher) {
    final int[] parts = new int[PARTS];
    for (int i = 0; i < PARTS; i++) {
      final String part = matcher.group(i + 1);
      parts[i] = part == null ? 0 : Integer.parseInt(part);
    }
    return parts;
  }
}
