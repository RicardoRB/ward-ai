package dev.ward.infrastructure.dependency;

import dev.ward.domain.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Finds the directory of a workspace root that holds a project's lockfile. */
public final class LockFiles {
  private LockFiles() {}

  /**
   * Returns the nearest directory from the project root up to the repository root that holds one of
   * {@code names}, or the project root when none does.
   */
  public static Path rootWith(final Project project, final List<String> names) {
    Path dir = project.root();
    while (dir != null && dir.startsWith(project.repositoryRoot())) {
      if (first(dir, names).isPresent()) {
        return dir;
      }
      dir = dir.getParent();
    }
    return project.root();
  }

  /** Returns the first of {@code names} that is a file in {@code dir}. */
  public static Optional<String> first(final Path dir, final List<String> names) {
    return names.stream().filter(name -> Files.isRegularFile(dir.resolve(name))).findFirst();
  }

  /** Returns {@code file} relative to {@code base} with {@code /} separators. */
  public static String relative(final Path base, final Path file) {
    return base.relativize(file).toString().replace('\\', '/');
  }

  /** Joins two relative paths with {@code /}, leaving out an empty one. */
  public static String join(final String first, final String second) {
    if (first.isEmpty()) {
      return second;
    }
    return second.isEmpty() ? first : first + "/" + second;
  }
}
