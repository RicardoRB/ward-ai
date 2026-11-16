package dev.ward.analysis.domain.rule;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Selects files by project-relative path, the same in every language, and code outside the project
 * by the name it is imported with. A file is selected when it matches an entry of each of {@code
 * paths} and {@code directories} that is set, and no entry of {@code excludePaths}; a selector with
 * neither selects no file.
 *
 * @param paths globs on the path; empty when the selector does not filter by path
 * @param directories plain directory names, compared as layer membership compares them
 * @param excludePaths globs of the paths that are left out
 * @param external package or module names outside the project; an entry also names everything
 *     below it
 */
public record ArchitectureSelector(
    List<String> paths,
    List<String> directories,
    List<String> excludePaths,
    List<String> external
) {
  private static final String WILDCARD = "*";
  private static final List<String> SEGMENT_ENDS = List.of(".", "/");

  /**
   * Copies the lists and checks that the selector selects by {@code paths}, {@code directories} or
   * {@code external}.
   *
   * @throws IllegalArgumentException if no list is set, a glob is malformed or an external name
   *     holds a wildcard
   */
  public ArchitectureSelector {
    paths = List.copyOf(paths);
    directories = List.copyOf(directories);
    excludePaths = List.copyOf(excludePaths);
    external = List.copyOf(external);
    if (paths.isEmpty() && directories.isEmpty() && external.isEmpty()) {
      throw new IllegalArgumentException(
          "a selector needs paths or directories (or external, in must-not-depend-on)"
      );
    }
    if (Stream.of(paths, directories, excludePaths, external)
        .flatMap(List::stream)
        .anyMatch(String::isEmpty)) {
      throw new IllegalArgumentException("a selector entry must not be empty");
    }
    if (external.stream().anyMatch(name -> name.contains(WILDCARD))) {
      throw new IllegalArgumentException(
          "an external name has no wildcard; a name also matches everything below it"
      );
    }
    matcher(paths);
    matcher(excludePaths);
  }

  /** Creates a selector of project files only. */
  public ArchitectureSelector(
      final List<String> paths,
      final List<String> directories,
      final List<String> excludePaths
  ) {
    this(paths, directories, excludePaths, List.of());
  }

  /**
   * Returns the test that tells whether an imported package or module name is one of {@code
   * external}: the entry itself, or a name that continues it after {@code .} or {@code /}.
   */
  public Predicate<String> externalMatcher() {
    return name ->
        external.stream()
            .anyMatch(
                entry ->
                    name.equals(entry)
                        || SEGMENT_ENDS.stream().anyMatch(end -> name.startsWith(entry + end))
            );
  }

  /** Returns the test that tells whether a project-relative path is selected. */
  public Predicate<String> matcher() {
    final Predicate<String> inPaths = matcher(paths);
    final Predicate<String> excluded = matcher(excludePaths);
    if (paths.isEmpty() && directories.isEmpty()) {
      return path -> false;
    }
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
