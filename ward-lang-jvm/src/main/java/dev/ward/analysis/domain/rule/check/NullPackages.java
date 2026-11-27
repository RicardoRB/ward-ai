package dev.ward.analysis.domain.rule.check;

import java.util.List;

/** Reads what the annotations of a package say about nullness. */
final class NullPackages {
  private static final String MARKED = "org.jspecify.annotations.NullMarked";
  private static final String UNMARKED = "org.jspecify.annotations.NullUnmarked";

  private NullPackages() {}

  /** Tells whether the package's annotations mark it {@code @NullMarked}. */
  static boolean marked(final List<String> packageAnnotations) {
    return packageAnnotations.contains(MARKED) && !packageAnnotations.contains(UNMARKED);
  }
}
