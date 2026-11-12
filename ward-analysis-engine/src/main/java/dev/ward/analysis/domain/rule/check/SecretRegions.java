package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.TextFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses where in a text a secret rule matches its regex. A single-line rule matches line by line,
 * and a line over {@link #LONG_LINE} characters only in windows around the keyword hits; a rule
 * that spans lines matches in windows that start {@link #LEAD} characters before a keyword hit, for
 * a regex whose keyword is not its first word. Bounding the text a regex sees is what keeps Java's
 * backtracking regex fast on a minified file.
 */
final class SecretRegions {

  /** A line longer than this is matched in windows. */
  static final int LONG_LINE = 4096;

  private static final int OVERLAP = 256;
  private static final int TEXT_OVERLAP = 4096;
  private static final int TEXT_WINDOW = 16 * 1024;
  private static final int LEAD = 512;

  private SecretRegions() {}

  /**
   * Returns the sorted {@code [start, end)} regions where {@code rule} matches; windows may overlap
   * a little, so a match is found in either.
   */
  static List<int[]> of(final TextFile file, final SecretRule rule) {
    final int[] hits = hits(file.lower(), rule.keywords());
    return rule.multiline()
        ? textWindows(file.text().length(), hits, !rule.keywords().isEmpty())
        : lines(file.text(), hits, !rule.keywords().isEmpty());
  }

  private static int[] hits(final String lower, final List<String> keywords) {
    final List<Integer> found = new ArrayList<>();
    for (final String keyword : keywords) {
      int at = lower.indexOf(keyword);
      while (at >= 0) {
        found.add(at);
        at = lower.indexOf(keyword, at + 1);
      }
    }
    return found.stream().mapToInt(Integer::intValue).sorted().toArray();
  }

  private static List<int[]> textWindows(
      final int length, final int[] hits, final boolean hasKeywords) {
    if (!hasKeywords) {
      return slide(0, length, TEXT_WINDOW);
    }
    return windows(hits, 0, 0, length, TEXT_WINDOW, TEXT_OVERLAP);
  }

  private static List<int[]> lines(final String text, final int[] hits, final boolean keywords) {
    final List<int[]> regions = new ArrayList<>();
    int next = 0;
    int start = 0;
    while (start < text.length()) {
      final int newline = text.indexOf('\n', start);
      final int end = newline < 0 ? text.length() : newline + 1;
      if (end - start <= LONG_LINE) {
        regions.add(new int[] {start, end});
      } else {
        while (next < hits.length && hits[next] < start) {
          next++;
        }
        regions.addAll(longLine(start, end, hits, next, keywords));
      }
      start = end;
    }
    return regions;
  }

  private static List<int[]> longLine(
      final int start, final int end, final int[] hits, final int from, final boolean keywords) {
    if (!keywords) {
      return slide(start, end, LONG_LINE);
    }
    return windows(hits, from, start, end, LONG_LINE, OVERLAP);
  }

  /**
   * Returns windows of {@code size} characters that start {@link #LEAD} before a keyword hit and
   * stop at {@code limit}. The next window starts at the first hit that is within {@code overlap}
   * of the end of the last one, or after it, so the windows overlap by little and their total size
   * stays near the size of the text, however many hits there are.
   */
  private static List<int[]> windows(
      final int[] hits,
      final int from,
      final int floor,
      final int limit,
      final int size,
      final int overlap) {
    final List<int[]> windows = new ArrayList<>();
    int next = from;
    while (next < hits.length && hits[next] < limit) {
      final int start = Math.max(floor, hits[next] - LEAD);
      final int end = Math.min(limit, start + size);
      windows.add(new int[] {start, end});
      final int resume = end - overlap;
      while (next < hits.length && hits[next] < resume && end < limit) {
        next++;
      }
      if (end >= limit) {
        break;
      }
    }
    return windows;
  }

  private static List<int[]> slide(final int start, final int end, final int size) {
    final List<int[]> windows = new ArrayList<>();
    for (int at = start; at < end; at += size - OVERLAP) {
      windows.add(new int[] {at, Math.min(end, at + size)});
      if (at + size >= end) {
        break;
      }
    }
    return windows;
  }
}
