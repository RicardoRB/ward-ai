package dev.ward.analysis.domain.rust;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Rewrites the path of a {@code use} written inside inline modules into a path from the file's own
 * module. The lists are live views of the walk that owns them.
 */
final class RustImportPaths {
  private static final String SELF = "self";
  private static final String SUPER = "super";

  private final List<String> inline;
  private final Set<String> children;

  RustImportPaths(final List<String> inline, final Set<String> children) {
    this.inline = inline;
    this.children = children;
  }

  /** Rewrites a path written inside inline modules into a path from the file's own module. */
  List<String> rewritten(final List<String> segments) {
    final String first = segments.getFirst();
    if (SELF.equals(first)) {
      return join(List.of(SELF), inline, segments.subList(1, segments.size()));
    }
    if (SUPER.equals(first)) {
      return superPath(segments);
    }
    if (inline.isEmpty() && children.contains(first)) {
      return join(List.of(SELF), List.of(), segments);
    }
    return segments;
  }

  private List<String> superPath(final List<String> segments) {
    int ups = 0;
    while (ups < segments.size() && SUPER.equals(segments.get(ups))) {
      ups++;
    }
    final List<String> rest = segments.subList(ups, segments.size());
    if (ups <= inline.size()) {
      return join(List.of(SELF), inline.subList(0, inline.size() - ups), rest);
    }
    return join(Collections.nCopies(ups - inline.size(), SUPER), List.of(), rest);
  }

  private static List<String> join(
      final List<String> head,
      final List<String> middle,
      final List<String> tail
  ) {
    final List<String> all = new ArrayList<>(head);
    all.addAll(middle);
    all.addAll(tail);
    return all;
  }
}
