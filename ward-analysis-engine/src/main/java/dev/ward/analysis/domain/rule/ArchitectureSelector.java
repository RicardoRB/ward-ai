package dev.ward.analysis.domain.rule;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Selects files by project-relative path, the same in every language. A file is selected when it
 * matches an entry of each of {@code paths} and {@code directories} that is set, and no entry of
 * {@code excludePaths}.
 *
 * @param paths globs on the path; empty when the selector does not filter by path
 * @param directories plain directory names, compared as layer membership compares them
 * @param excludePaths globs of the paths that are left out
 */
public record ArchitectureSelector(
    List<String> paths, List<String> directories, List<String> excludePaths) {

  /**
   * Copies the lists and checks that the selector selects by {@code paths} or {@code directories}.
   *
   * @throws IllegalArgumentException if neither list is set or a glob is malformed
   */
  public ArchitectureSelector {
    paths = List.copyOf(paths);
    directories = List.copyOf(directories);
    excludePaths = List.copyOf(excludePaths);
    if (paths.isEmpty() && directories.isEmpty()) {
      throw new IllegalArgumentException("a selector needs paths or directories");
    }
    if (Stream.of(paths, directories, excludePaths)
        .flatMap(List::stream)
        .anyMatch(String::isEmpty)) {
      throw new IllegalArgumentException("a selector entry must not be empty");
    }
    matcher(paths);
    matcher(excludePaths);
  }

  /** Returns the test that tells whether a project-relative path is selected. */
  public Predicate<String> matcher() {
    final Predicate<String> inPaths = matcher(paths);
    final Predicate<String> excluded = matcher(excludePaths);
    return path ->
        (paths.isEmpty() || inPaths.test(path))
            && (directories.isEmpty()
                || directories.stream().anyMatch(name -> LayerMembership.inLayer(path, name)))
            && !excluded.test(path);
  }

  private static Predicate<String> matcher(final List<String> globs) {
    final List<Pattern> patterns = globs.stream().map(ExcludeGlob::compile).toList();
    return path -> patterns.stream().anyMatch(pattern -> pattern.matcher(path).matches());
  }
}
