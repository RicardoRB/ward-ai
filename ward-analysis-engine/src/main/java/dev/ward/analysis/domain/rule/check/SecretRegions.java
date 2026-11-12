package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.TextFile;
import java.util.ArrayList;
import java.util.Arrays;
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

  private static final int HALF_WINDOW = LONG_LINE / 2;
  private static final int OVERLAP = 256;
  private static final int TEXT_WINDOW = 16 * 1024;
  private static final int LEAD = 512;

  private SecretRegions() {}

  /** Returns the sorted, disjoint {@code [start, end)} regions where {@code rule} matches. */
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
    final List<int[]> windows = new ArrayList<>();
    for (final int hit : hits) {
      windows.add(new int[] {Math.max(0, hit - LEAD), Math.min(length, hit + TEXT_WINDOW)});
    }
    return merge(windows);
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
    final List<int[]> windows = new ArrayList<>();
    for (int index = from; index < hits.length && hits[index] < end; index++) {
      windows.add(
          new int[] {
            Math.max(start, hits[index] - HALF_WINDOW), Math.min(end, hits[index] + HALF_WINDOW)
          });
    }
    return merge(windows);
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

  private static List<int[]> merge(final List<int[]> windows) {
    final List<int[]> merged = new ArrayList<>();
    for (final int[] window : windows) {
      final int[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
      if (last != null && window[0] <= last[1]) {
        last[1] = Math.max(last[1], window[1]);
      } else {
        merged.add(Arrays.copyOf(window, 2));
      }
    }
    return merged;
  }
}
