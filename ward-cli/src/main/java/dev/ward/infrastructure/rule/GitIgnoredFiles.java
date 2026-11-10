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
   * Returns the untracked files that Git ignores, relative to {@code root} with {@code /}
   * separators. When Git cannot answer, the result is empty, so no file is dropped.
   *
   * @param root the project root
   * @param executor the command executor
   * @return the ignored files, possibly empty
   */
  public static Set<String> of(final Path root, final CommandExecutor executor) {
    final CommandResult result =
        executor.execute(List.of("git", "ls-files", "-o", "-i", "--exclude-standard", "-z"), root);
    if (result.executionError() != null || result.exitCode() != 0) {
      return Set.of();
    }
    return Set.of(result.stdout().split("\\u0000"));
  }
}
