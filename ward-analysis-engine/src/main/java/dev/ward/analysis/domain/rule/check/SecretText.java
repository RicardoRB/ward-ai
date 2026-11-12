package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

/** The text-level helpers of a secret match: the secret, its line, and positions. */
final class SecretText {

  private SecretText() {}

  /** The secret group, else the first group that matched, else the whole match. */
  static String secret(final Matcher matcher, final int secretGroup) {
    String secret = matcher.group();
    if (secretGroup > 0) {
      secret = matcher.group(secretGroup);
    } else {
      for (int group = 1; group <= matcher.groupCount(); group++) {
        final String value = matcher.group(group);
        if (value != null && !value.isEmpty()) {
          secret = value;
          break;
        }
      }
    }
    return secret == null ? "" : trimNewlines(secret);
  }

  static String trimNewlines(final String value) {
    int from = 0;
    int to = value.length();
    while (from < to && value.charAt(from) == '\n') {
      from++;
    }
    while (to > from && value.charAt(to - 1) == '\n') {
      to--;
    }
    return value.substring(from, to);
  }

  static String line(final String text, final int[] region, final int at) {
    final int start = Math.max(region[0], text.lastIndexOf('\n', at - 1) + 1);
    final int newline = text.indexOf('\n', at);
    final int end = newline < 0 ? region[1] : Math.min(region[1], newline);
    return text.substring(start, Math.max(start, end));
  }

  static int[] lineStarts(final String text) {
    final List<Integer> starts = new ArrayList<>();
    starts.add(0);
    for (int at = text.indexOf('\n'); at >= 0; at = text.indexOf('\n', at + 1)) {
      starts.add(at + 1);
    }
    return starts.stream().mapToInt(Integer::intValue).toArray();
  }

  static Span span(final int[] lineStarts, final Matcher matcher) {
    final int[] begin = position(lineStarts, matcher.start());
    final int[] finish = position(lineStarts, matcher.end());
    return new Span(begin[0], begin[1], finish[0], finish[1], matcher.start(), matcher.end());
  }

  /** Returns the 1-based line and column of {@code offset}. */
  static int[] position(final int[] lineStarts, final int offset) {
    final int found = Arrays.binarySearch(lineStarts, offset);
    final int index = found >= 0 ? found : -found - 2;
    return new int[] {index + 1, offset - lineStarts[index] + 1};
  }
}
