package dev.ward.infrastructure.secrets;

import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.secrets.GitHistory;
import dev.ward.domain.secrets.HistoryPatch;
import dev.ward.domain.secrets.HistoryUnavailableException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Reads the history with {@code git}, run as an argument vector. It first refuses a shallow clone,
 * then reads the commits in batches so that the output of one call stays bounded. A batch over
 * the limit is halved and retried; one commit over the limit is read file by file.
 */
public final class GitCliHistory implements GitHistory {
  /** Commits per {@code git log} call. */
  static final int BATCH = 200;

  /** The most characters of patch text one batch may hold. */
  static final int BATCH_LIMIT = 64 * 1024 * 1024;

  private static final Logger LOGGER = Logger.getLogger(GitCliHistory.class.getName());

  private static final String MARKER = "\u0001ward-commit ";
  private static final int SHORT_ID = 8;
  private static final int SINGLE = 1;
  private static final int HALVES = 2;
  private static final String SHALLOW = "true";

  private final CommandExecutor executor;

  /** Creates the adapter over {@code executor}. */
  @Inject
  public GitCliHistory(final CommandExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void patches(
      final Path root,
      final Optional<LocalDate> since,
      final Consumer<List<HistoryPatch>> sink
  ) {
    requireFullHistory(root);
    int skip = 0;
    while (true) {
      final int commits = readRange(root, since, skip, BATCH, sink);
      if (commits == 0) {
        return;
      }
      skip += commits;
    }
  }

  /** Reads up to {@code count} commits from {@code skip}; a range over the limit is halved. */
  private int readRange(
      final Path root,
      final Optional<LocalDate> since,
      final int skip,
      final int count,
      final Consumer<List<HistoryPatch>> sink
  ) {
    final String output = log(root, since, skip, count);
    if (output.length() > BATCH_LIMIT) {
      return splitOrReadAlone(root, since, skip, count, sink);
    }
    final int commits = PatchParser.commits(output, MARKER);
    if (commits == 0) {
      return 0;
    }
    sink.accept(PatchParser.parse(output, MARKER, SHORT_ID));
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("event=secrets-history commits=" + commits + " skipped=" + skip);
    }
    return count;
  }

  private int splitOrReadAlone(
      final Path root,
      final Optional<LocalDate> since,
      final int skip,
      final int count,
      final Consumer<List<HistoryPatch>> sink
  ) {
    if (count > SINGLE) {
      final int half = count / HALVES;
      LOGGER.fine("event=secrets-history-split batch=" + count + " retry=" + half);
      return readRange(root, since, skip, half, sink);
    }
    new LargeCommitReader(executor, MARKER, SHORT_ID).read(root, since, skip, sink);
    return SINGLE;
  }

  private void requireFullHistory(final Path root) {
    final CommandResult result =
        executor.execute(List.of("git", "rev-parse", "--is-shallow-repository"), root);
    if (result.hasExecutionError()) {
      throw new HistoryUnavailableException("git could not be started to read the history");
    }
    if (!result.succeeded()) {
      throw new HistoryUnavailableException("the project is not in a Git work tree");
    }
    if (SHALLOW.equals(result.stdout().strip())) {
      throw new HistoryUnavailableException(
          "the clone is shallow; fetch the full history (for example fetch-depth: 0) to scan it"
      );
    }
  }

  private String log(
      final Path root,
      final Optional<LocalDate> since,
      final int skip,
      final int count
  ) {
    final List<String> command = new ArrayList<>();
    command.addAll(
        List.of(
            "git",
            "log",
            "--all",
            "-p",
            "-U0",
            "--no-color",
            "--no-ext-diff",
            "--no-textconv",
            "--format=" + MARKER + "%H",
            "--max-count=" + count,
            "--skip=" + skip
        )
    );
    since.ifPresent(day -> command.add("--since=" + day));
    final CommandResult result = executor.execute(command, root);
    if (result.hasExecutionError()) {
      throw new HistoryUnavailableException("git could not be started to read the history");
    }
    if (!result.succeeded()) {
      throw new HistoryUnavailableException("git could not read the history");
    }
    return result.stdout();
  }
}
