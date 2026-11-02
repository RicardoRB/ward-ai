package dev.ward.domain.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.project.Project;
import java.util.List;

/**
 * Describes, without writing, the module build scripts a project's build would change for these
 * gates. Kept apart from {@link BuildToolConfiguration} so that port stays a size every adapter can
 * implement.
 */
@FunctionalInterface
public interface BuildModulePlanning {
  /**
   * Returns the module scripts {@code gates} would change, one entry per script that would change.
   */
  List<PlannedFileChange> planModules(
      Project project, List<InitGateOption> gates, QualityPreset preset, boolean approved);

  /** Plans no module scripts, for a catalog whose projects have none. */
  static BuildModulePlanning none() {
    return (project, gates, preset, approved) -> List.of();
  }
}
