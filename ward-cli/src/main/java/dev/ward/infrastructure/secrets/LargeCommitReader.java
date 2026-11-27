package dev.ward.infrastructure.secrets;

import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.secrets.HistoryPatch;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads one commit whose whole patch is over the batch limit, file by file. A file that is still
 * over the limit, or that git cannot read, is skipped with a warning that names the commit and
 * the number of files, never their content.
 */
final class LargeCommitReader {
  private static final Logger LOGGER = Logger.getLogger(LargeCommitReader.class.getName());
  private static final String NUL = "\u0000";
  private static final String LITERAL = ":(literal)";

  private final CommandExecutor executor;
  private final String marker;
  private final int shortId;

  LargeCommitReader(final CommandExecutor executor, final String marker, final int shortId) {
    this.executor = executor;
    this.marker = marker;
    this.shortId = shortId;
  }

  /** Scans the commit at position {@code skip} of the history, one file at a time. */
  void read(
      final Path root,
      final Optional<LocalDate> since,
      final int skip,
      final Consumer<List<HistoryPatch>> sink
  ) {
    final Optional<String> id = commitId(root, since, skip);
    if (id.isEmpty()) {
      LOGGER.warning("event=secrets-history-skip skipped=commit reason=unreadable");
      return;
    }
    final String commit = id.get();
    final String shortCommit = commit.substring(0, Math.min(shortId, commit.length()));
    final Optional<List<String>> files = files(root, commit);
    if (files.isEmpty()) {
      LOGGER.warning("event=secrets-history-skip commit=" + shortCommit + " reason=unreadable");
      return;
    }
    int skippedFiles = 0;
    for (final String file : files.get()) {
      if (!readFile(root, commit, file, sink)) {
        skippedFiles++;
      }
    }
    if (skippedFiles > 0) {
      LOGGER.warning(
          "event=secrets-history-skip commit="
              + shortCommit
              + " files="
              + skippedFiles
              + " reason=over-limit"
      );
    } else if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("event=secrets-history-split commit=" + shortCommit + " read=per-file");
    }
  }

  private Optional<String> commitId(
      final Path root,
      final Optional<LocalDate> since,
      final int skip
  ) {
    final List<String> command =
        new ArrayList<>(List.of("git", "rev-list", "--all", "--max-count=1", "--skip=" + skip));
    since.ifPresent(day -> command.add("--since=" + day));
    final CommandResult result = executor.execute(command, root);
    final String id = result.stdout().strip();
    if (!result.succeeded() || id.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(id);
  }

  private Optional<List<String>> files(final Path root, final String commit) {
    final CommandResult result =
        executor.execute(
            List.of(
                "git",
                "diff-tree",
                "--root",
                "-r",
                "--name-only",
                "-z",
                "--no-commit-id",
                commit
            ),
            root
        );
    if (!result.succeeded()) {
      return Optional.empty();
    }
    final List<String> files = new ArrayList<>(List.of(result.stdout().split(NUL)));
    files.removeIf(String::isEmpty);
    return Optional.of(files);
  }

  private boolean readFile(
      final Path root,
      final String commit,
      final String file,
      final Consumer<List<HistoryPatch>> sink
  ) {
    final CommandResult result =
        executor.execute(
            List.of(
                "git",
                "diff-tree",
                "-p",
                "--root",
                "-U0",
                "--no-color",
                "--no-ext-diff",
                "--no-textconv",
                "--format=" + marker + "%H",
                commit,
                "--",
                LITERAL + file
            ),
            root
        );
    if (!result.succeeded() || result.stdout().length() > GitCliHistory.BATCH_LIMIT) {
      return false;
    }
    sink.accept(PatchParser.parse(result.stdout(), marker, shortId));
    return true;
  }
}
