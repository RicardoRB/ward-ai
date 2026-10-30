package dev.ward.domain.init;

import java.nio.file.Path;
import java.util.List;

/**
 * What writing a Turborepo workspace changed, so it can be rolled back.
 *
 * @param written each file changed, with its content before the change, in the order changed
 * @param kept the scripts kept because they already differ from the plan
 */
public record TurboWorkspaceChange(List<WrittenFile> written, List<String> kept) {
  /** Copies the lists. */
  public TurboWorkspaceChange {
    written = List.copyOf(written);
    kept = List.copyOf(kept);
  }

  /**
   * One file as it was before the change.
   *
   * @param file the changed file
   * @param original its content before the change
   */
  public record WrittenFile(Path file, String original) {}
}
