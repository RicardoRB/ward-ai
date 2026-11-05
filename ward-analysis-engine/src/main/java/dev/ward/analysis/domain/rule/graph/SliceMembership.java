package dev.ward.analysis.domain.rule.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Assigns files to slices: the first directory below the longest root that holds the file. A file
 * directly in a root, or outside every root, belongs to no slice.
 */
final class SliceMembership {

  private final List<String> roots;

  /** Creates the membership of {@code roots}, longest first so the longest root wins. */
  SliceMembership(final List<String> roots) {
    this.roots =
        roots.stream()
            .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(root -> root))
            .toList();
  }

  /** Returns the slice of {@code path}, or empty when it belongs to none. */
  Optional<String> sliceOf(final String path) {
    for (final String root : roots) {
      final String prefix = root + "/";
      if (path.startsWith(prefix)) {
        final String rest = path.substring(prefix.length());
        final int slash = rest.indexOf('/');
        return slash < 0 ? Optional.empty() : Optional.of(rest.substring(0, slash));
      }
    }
    return Optional.empty();
  }
}
