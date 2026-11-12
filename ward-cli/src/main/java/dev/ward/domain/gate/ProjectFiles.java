package dev.ward.domain.gate;

import java.nio.file.Path;
import java.util.Optional;

/** Outbound port for gates that inspect files in the project working tree. */
public interface ProjectFiles {
  /** Returns whether the regular file {@code path}, relative to {@code root}, exists. */
  boolean exists(Path root, String path);

  /**
   * Returns whether the file {@code path}, relative to {@code root}, contains {@code text}; false
   * when the file cannot be read.
   */
  boolean contains(Path root, String path, String text);

  /**
   * Returns the text of the file {@code path}, relative to {@code root}, or empty when the file
   * does not exist, cannot be read, or lies outside {@code root}. Adapters that cannot read files
   * return empty.
   */
  default Optional<String> read(final Path root, final String path) {
    return Optional.empty();
  }
}
