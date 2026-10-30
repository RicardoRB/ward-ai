package dev.ward.application.init;

import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.util.Optional;

/** Chooses the strategy of a task runner. Each runner that has one is listed here. */
final class TaskRunnerStrategies {
  private TaskRunnerStrategies() {}

  /**
   * Returns the strategy of {@code runner}, or empty when it has none. A Turborepo member runs its
   * commands through the same strategy as its root.
   */
  static Optional<TaskRunnerStrategy> of(final TaskRunner runner) {
    return switch (runner) {
      case TURBO, TURBO_MEMBER -> Optional.of(new TurboTaskRunner());
      default -> Optional.empty();
    };
  }

  /** Returns the strategy of {@code project}'s task runner, or empty for a project without one. */
  static Optional<TaskRunnerStrategy> of(final Project project) {
    return of(project.taskRunner());
  }
}
