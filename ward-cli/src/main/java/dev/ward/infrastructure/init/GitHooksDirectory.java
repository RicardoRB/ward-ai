package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Asks Git which hooks directory it runs, honouring {@code core.hooksPath} and worktrees. */
public final class GitHooksDirectory {
  private static final Logger LOGGER = Logger.getLogger(GitHooksDirectory.class.getName());
  private static final List<String> COMMAND = List.of("git", "rev-parse", "--git-path", "hooks");

  private final CommandExecutor executor;

  /** Creates the resolver; {@code executor} runs the read-only Git query. */
  @Inject
  public GitHooksDirectory(final CommandExecutor executor) {
    this.executor = executor;
  }

  /**
   * Returns the hooks directory Git uses for {@code project}, or {@code .git/hooks} in its
   * repository root when Git cannot answer, as outside a Git work tree.
   */
  public Path resolve(final Project project) {
    final Path root = project.root().toAbsolutePath().normalize();
    final CommandResult result = executor.execute(COMMAND, root);
    final String answer = result.stdout() == null ? "" : result.stdout().strip();
    if (!result.succeeded() || answer.isEmpty() || answer.contains("\n")) {
      LOGGER.log(Level.INFO, "event=git-hooks-directory source=fallback");
      return project.repositoryRoot().toAbsolutePath().normalize().resolve(".git/hooks");
    }
    LOGGER.log(Level.INFO, "event=git-hooks-directory source=git");
    // Git prints the path relative to the directory it ran in unless it is absolute.
    return root.resolve(answer).normalize();
  }

  /** Returns this clone's {@code core.hooksPath} exactly as configured, when it is set. */
  public Optional<String> configuredPath(final Project project) {
    final CommandResult result =
        executor.execute(
            List.of("git", "config", "--local", "--get", "core.hooksPath"),
            project.root().toAbsolutePath().normalize()
        );
    final String value = result.stdout() == null ? "" : result.stdout().strip();
    return result.succeeded() && !value.isEmpty() ? Optional.of(value) : Optional.empty();
  }

  /** Sets this clone's {@code core.hooksPath} to {@code folder}. */
  public void setPath(final Project project, final String folder) {
    final CommandResult result =
        executor.execute(
            List.of("git", "config", "--local", "core.hooksPath", folder),
            project.root().toAbsolutePath().normalize()
        );
    if (!result.succeeded()) {
      throw new WardException("Could not set core.hooksPath: git exited with " + result.exitCode());
    }
    LOGGER.log(Level.INFO, "event=git-hooks-path action=set");
  }

  /** Restores this clone's {@code core.hooksPath} to {@code previous}, or unsets it when null. */
  public void restorePath(final Path root, final String previous) {
    executor.execute(
        previous == null
            ? List.of("git", "config", "--local", "--unset", "core.hooksPath")
            : List.of("git", "config", "--local", "core.hooksPath", previous),
        root
    );
    LOGGER.log(Level.INFO, "event=git-hooks-path action=restore");
  }
}
