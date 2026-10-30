package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;

/**
 * Refuses a Turborepo root before anything is written: init does not yet write its member scripts
 * or {@code turbo.json} tasks, so its gates would fail. Remove this once those are written.
 */
final class TurborepoRefusal {
  private TurborepoRefusal() {}

  /**
   * Throws if {@code project} is a Turborepo root.
   *
   * @throws WardException if the project is a Turborepo root
   */
  static void check(final Project project) {
    if (project.taskRunner() == TaskRunner.TURBO) {
      throw new WardException(
          "Ward init does not support Turborepo workspaces yet: it cannot write their member"
              + " scripts or turbo.json tasks, so the gates would fail. No files were changed.");
    }
  }
}
