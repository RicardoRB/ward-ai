package dev.ward.domain.init;

import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Optional;

/** Port for ownership-safe quality-hook installation. */
public interface QualityHookGeneration {
  /** Installs the Ward quality hooks where Git runs them, keeping user-owned content untouched. */
  default QualityHookChange apply(final Project project) {
    return apply(project, HookPlacement.GIT_RUNS);
  }

  /** Installs the Ward quality hooks at {@code placement}, keeping user-owned content untouched. */
  QualityHookChange apply(Project project, HookPlacement placement);

  /** Removes the Ward-owned quality hooks and reports what was removed. */
  default IntegrationResult remove(final Project project) {
    return remove(project, false);
  }

  /**
   * Removes the Ward-owned quality hooks, or only reports the files it would change when {@code
   * dryRun} is set.
   */
  IntegrationResult remove(Project project, boolean dryRun);

  /** Describes, without writing, what {@link #apply} would do with each hook file. */
  default List<PlannedFileChange> plan(final Project project) {
    return plan(project, HookPlacement.GIT_RUNS);
  }

  /** Describes, without writing, what {@link #apply} would do at {@code placement}. */
  List<PlannedFileChange> plan(Project project, HookPlacement placement);

  /** Returns this clone's {@code core.hooksPath}, when it is set. */
  Optional<String> hooksPath(Project project);

  /** Restores the hook files and {@code core.hooksPath} to their state before {@code change}. */
  void rollback(QualityHookChange change);
}
