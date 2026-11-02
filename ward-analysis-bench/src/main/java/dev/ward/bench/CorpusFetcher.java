package dev.ward.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Fetches one pinned commit into a cache checkout, using only argument vectors and never a branch.
 */
final class CorpusFetcher {

  private final CommandRunner runner;

  CorpusFetcher(final CommandRunner runner) {
    this.runner = runner;
  }

  /** Returns {@code cacheRoot/<name>} checked out at exactly the pinned commit. */
  Path fetch(final CorpusEntry entry, final Path cacheRoot) {
    final Path checkout = cacheRoot.resolve(entry.name());
    FileTrees.delete(checkout);
    try {
      Files.createDirectories(checkout);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not create the corpus checkout", e);
    }
    requireSuccess(entry, runner.run(List.of("git", "init", "--quiet"), checkout), "init failed");
    requireSuccess(
        entry,
        runner.run(
            List.of("git", "fetch", "--depth", "1", "--quiet", entry.url(), entry.commit()),
            checkout),
        "fetch failed");
    requireSuccess(
        entry,
        runner.run(List.of("git", "checkout", "--quiet", "FETCH_HEAD"), checkout),
        "checkout failed");
    final String head = runner.capture(List.of("git", "rev-parse", "HEAD"), checkout).strip();
    if (!head.equals(entry.commit())) {
      throw new CorpusFetchException(entry.name(), entry.commit(), "checkout resolved to " + head);
    }
    return checkout;
  }

  private static void requireSuccess(
      final CorpusEntry entry, final CommandResult result, final String reason) {
    if (result.exitCode() != 0) {
      throw new CorpusFetchException(entry.name(), entry.commit(), reason);
    }
  }
}
