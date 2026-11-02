package dev.ward.domain.init;

import java.nio.file.Path;

/**
 * Reversible generated architecture-test file change.
 *
 * @param file the architecture file the change wrote or reused
 * @param created whether the change created {@code file}
 * @param architecture the architecture style the file enforces
 * @param previous the content a rewrite replaced, or {@code null} when nothing was replaced
 */
public record ArchitectureTestChange(
    Path file, boolean created, String architecture, String previous) {
  /** Describes a change that writes no replaced content, such as a created or reused file. */
  public ArchitectureTestChange(final Path file, final boolean created, final String architecture) {
    this(file, created, architecture, null);
  }
}
