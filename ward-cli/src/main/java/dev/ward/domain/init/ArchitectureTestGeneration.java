package dev.ward.domain.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.project.Project;
import java.util.List;

/** Generates and rolls back an optional architecture test. */
public interface ArchitectureTestGeneration {
  /** Returns simple names of existing ArchUnit test classes in the project's test sources. */
  List<String> existingArchUnitTests(Project project);

  /**
   * Writes the ArchUnit test for {@code style}, or reuses an existing one; the change can be rolled
   * back.
   */
  ArchitectureTestChange apply(Project project, String style);

  /**
   * Writes the ArchUnit test with the preset's optional {@code extras}; the default ignores them.
   */
  default ArchitectureTestChange apply(
      final Project project, final String style, final QualityPreset.ArchitectureExtras extras) {
    return apply(project, style);
  }

  /** Describes, without writing, whether {@link #apply} would create the test. */
  PlannedFileChange plan(Project project, String style, QualityPreset.ArchitectureExtras extras);

  /** Removes a test that {@code change} created and leaves reused tests untouched. */
  void rollback(ArchitectureTestChange change);

  /**
   * Returns the project-relative source roots whose slices the {@code ward} engine checks for
   * cycles; none when the toolchain gives no roots.
   */
  default List<String> cycleRoots(final Project project) {
    return List.of();
  }

  /**
   * Returns the project-relative module directories that the {@code ward} engine's independence
   * table lists for {@code style}; none when the toolchain has no features to keep apart.
   */
  default List<String> independenceModules(final Project project, final String style) {
    return List.of();
  }
}
