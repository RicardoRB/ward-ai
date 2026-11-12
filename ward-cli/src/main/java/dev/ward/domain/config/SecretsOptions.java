package dev.ward.domain.config;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The options of a {@code secrets} check.
 *
 * @param scanHistory whether the check also scans the lines that every commit added
 * @param historySince the first day of the history to scan, or empty for all of it
 * @param excludePaths globs of the project-relative paths no secrets rule reads
 */
public record SecretsOptions(
    boolean scanHistory, Optional<LocalDate> historySince, List<String> excludePaths) {

  /** The options of a check that sets none. */
  public static final SecretsOptions NONE = new SecretsOptions(false, Optional.empty(), List.of());

  /** Copies the globs. */
  public SecretsOptions {
    excludePaths = List.copyOf(excludePaths);
  }
}
