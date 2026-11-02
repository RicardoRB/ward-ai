package dev.ward.domain.gate;

import java.nio.file.Path;

/** Port that prepares the places gate tools expect to write reports into. */
@FunctionalInterface
public interface ReportDirectories {
  ReportDirectories NONE = (projectRoot, reportPath) -> {};

  /**
   * Creates the parent directory of {@code reportPath} when it resolves inside {@code projectRoot}.
   * Failures raise {@link ReportWriteException}.
   */
  void prepareParent(Path projectRoot, String reportPath);

  /**
   * Removes the file or directory an earlier run left at {@code reportPath} and creates its parent,
   * when it resolves inside {@code projectRoot}. Failures raise {@link ReportWriteException}. Ports
   * without a file system only prepare the parent.
   */
  default void prepareFresh(final Path projectRoot, final String reportPath) {
    prepareParent(projectRoot, reportPath);
  }
}
