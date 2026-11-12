package dev.ward.infrastructure.gate;

import dev.ward.domain.gate.RevisionFiles;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/** Resolves revisions with fixed Git argument vectors; never fetches or writes. */
public final class GitRevisionFiles implements RevisionFiles {
  private static final String GIT = "git";
  private final CommandExecutor executor;

  /** Creates the adapter; {@code executor} runs Git. */
  @Inject
  public GitRevisionFiles(final CommandExecutor executor) {
    this.executor = executor;
  }

  @Override
  public Optional<String> resolve(final Path root, final String revision) {
    return commit(
        root,
        List.of(
            GIT, "rev-parse", "--verify", "--quiet", "--end-of-options", revision + "^{commit}"));
  }

  @Override
  public Optional<String> mergeBase(final Path root, final String first, final String second) {
    return commit(root, List.of(GIT, "merge-base", "--end-of-options", first, second));
  }

  // ./ makes Git resolve the path from the project root, which may be below the repository root.
  @Override
  public boolean exists(final Path root, final String commit, final String path) {
    return executor
        .execute(List.of(GIT, "cat-file", "-e", commit + ":./" + path), root)
        .succeeded();
  }

  // ls-tree prints paths relative to the working directory, which is the project root.
  @Override
  public List<String> files(final Path root, final String commit, final String directory) {
    final String base = directory.isEmpty() ? "." : directory;
    final CommandResult result =
        executor.execute(
            List.of(GIT, "ls-tree", "-r", "-z", "--name-only", commit, "--", base), root);
    if (!result.succeeded()) {
      return List.of();
    }
    final String prefix = directory.isEmpty() ? "" : directory + "/";
    return Arrays.stream(result.stdout().split("\0"))
        .filter(name -> !name.isEmpty() && name.startsWith(prefix))
        .map(name -> name.substring(prefix.length()))
        .toList();
  }

  // Git has no batch read through the executor, which has no standard input: one process per file.
  @Override
  public Optional<String> read(final Path root, final String commit, final String path) {
    final CommandResult result =
        executor.execute(List.of(GIT, "cat-file", "blob", commit + ":./" + path), root);
    return result.succeeded() ? Optional.of(result.stdout()) : Optional.empty();
  }

  @Override
  public List<String> tagsReachableFrom(final Path root, final String commit) {
    return lines(executor.execute(List.of(GIT, "tag", "--merged", commit), root));
  }

  @Override
  public List<String> tagsAt(final Path root, final String commit) {
    return lines(executor.execute(List.of(GIT, "tag", "--points-at", commit), root));
  }

  @Override
  public boolean isShallow(final Path root) {
    return "true"
        .equals(
            executor
                .execute(List.of(GIT, "rev-parse", "--is-shallow-repository"), root)
                .stdout()
                .trim());
  }

  private static List<String> lines(final CommandResult result) {
    if (!result.succeeded()) {
      return List.of();
    }
    return result.stdout().lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
  }

  private Optional<String> commit(final Path root, final List<String> command) {
    final CommandResult result = executor.execute(command, root);
    final String output = result.stdout().trim();
    return result.succeeded() && !output.isEmpty() ? Optional.of(output) : Optional.empty();
  }
}
