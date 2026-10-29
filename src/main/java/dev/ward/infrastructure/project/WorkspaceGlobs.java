package dev.ward.infrastructure.project;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

/** Matches a workspace member directory against workspace glob patterns relative to its root. */
final class WorkspaceGlobs {
  private WorkspaceGlobs() {}

  /**
   * Returns whether {@code candidate}, a directory below {@code root}, matches one of the {@code
   * patterns}, which are relative to {@code root}. The root itself never matches.
   */
  static boolean matchesAny(final List<String> patterns, final Path root, final Path candidate) {
    if (root.equals(candidate)) {
      return false;
    }
    final Path relative = Path.of(root.relativize(candidate).toString().replace('\\', '/'));
    return patterns.stream()
        .map(pattern -> pattern.replaceFirst("^\\./", "").replaceFirst("/$", ""))
        .anyMatch(
            glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob).matches(relative));
  }
}
