package dev.ward.infrastructure.rule;

import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** The untracked files that Git ignores under a project root, which Ward does not read. */
public final class GitIgnoredFiles {

  private GitIgnoredFiles() {}

  /**
   * Tells whether {@code ignored} covers {@code path}: the path itself is ignored, or a folder that
   * contains it is ignored whole.
   */
  public static boolean covers(final Set<String> ignored, final String path) {
    if (ignored.contains(path)) {
      return true;
    }
    int slash = path.indexOf('/');
    while (slash >= 0) {
      if (ignored.contains(path.substring(0, slash + 1))) {
        return true;
      }
      slash = path.indexOf('/', slash + 1);
    }
    return false;
  }

  /**
   * Returns the untracked files and folders that Git ignores, relative to {@code root} with {@code
   * /} separators. A folder that Git ignores whole is one entry ending in {@code /}, so a large
   * tree such as {@code node_modules} costs one entry. When Git cannot answer, the result is empty,
   * so no file is dropped.
   *
   * @param root the project root
   * @param executor the command executor
   * @return the ignored files, possibly empty
   */
  public static Set<String> of(final Path root, final CommandExecutor executor) {
    final CommandResult result =
        executor.execute(
            List.of("git", "ls-files", "-o", "-i", "--exclude-standard", "--directory", "-z"),
            root);
    if (result.executionError() != null || result.exitCode() != 0) {
      return Set.of();
    }
    return Set.of(result.stdout().split("\\u0000"));
  }
}
