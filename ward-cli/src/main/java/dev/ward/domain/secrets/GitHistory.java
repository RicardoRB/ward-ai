package dev.ward.domain.secrets;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Port that yields the lines every commit of a repository added, for the history scan of the {@code
 * secrets} check.
 */
public interface GitHistory {

  /**
   * Hands the added lines of every commit reachable from any ref to {@code sink}, in batches,
   * newest commit first. A merge commit adds no lines. Nothing is read from a remote.
   *
   * @param root the project root, inside a Git work tree
   * @param since only commits made on or after this day, or all of them when empty
   * @param sink receives each batch
   * @throws HistoryUnavailableException if the history cannot be read, including a shallow clone
   */
  void patches(Path root, Optional<LocalDate> since, Consumer<List<HistoryPatch>> sink);
}
