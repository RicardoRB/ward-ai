package dev.ward.domain.gate;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes release tags, such as {@code v1.2.3}, and orders them by version. */
public final class ReleaseTags {
  private static final Pattern RELEASE = Pattern.compile("v?(\\d{1,9}(?:\\.\\d{1,9})+)");
  private static final String SEPARATOR = "\\.";

  private ReleaseTags() {}

  /**
   * Returns whether {@code tag} is a release: {@code MAJOR.MINOR} or more numeric segments with an
   * optional leading {@code v} and no suffix.
   */
  public static boolean isRelease(final String tag) {
    return RELEASE.matcher(tag).matches();
  }

  /** Returns the release among {@code tags} with the highest version, comparing numerically. */
  public static Optional<String> highest(final Collection<String> tags) {
    return tags.stream()
        .filter(ReleaseTags::isRelease)
        .max(Comparator.comparing(ReleaseTags::version, ReleaseTags::compare));
  }

  private static List<Integer> version(final String tag) {
    final Matcher matcher = RELEASE.matcher(tag);
    return matcher.matches()
        ? Arrays.stream(matcher.group(1).split(SEPARATOR)).map(Integer::valueOf).toList()
        : List.of();
  }

  private static int compare(final List<Integer> first, final List<Integer> second) {
    for (int index = 0; index < Math.max(first.size(), second.size()); index++) {
      final int left = index < first.size() ? first.get(index) : 0;
      final int right = index < second.size() ? second.get(index) : 0;
      if (left != right) {
        return Integer.compare(left, right);
      }
    }
    return 0;
  }
}
