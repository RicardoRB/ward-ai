package dev.ward.domain.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Applies and rolls back build-tool declarations needed by selected setup options. */
public interface BuildToolConfiguration {
  /** Adds the build plugins {@code gates} need and returns the change for rollback. */
  BuildFileChange apply(Project project, List<InitGateOption> gates);

  /**
   * Adds the build plugins {@code gates} need, configured for {@code preset}; the default ignores
   * the preset.
   */
  default BuildFileChange apply(
      final Project project, final List<InitGateOption> gates, final QualityPreset preset) {
    return apply(project, gates);
  }

  /**
   * Adds the build plugins {@code gates} need; plugin version updates are applied only when {@code
   * approval} accepts them. The default applies no updates.
   */
  default BuildFileChange apply(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Predicate<? super CompatibilityFinding> approval) {
    return apply(project, gates, preset);
  }

  /**
   * Describes, without writing, how the build file would change for these gates. Version updates
   * that need approval are listed as pending unless {@code approved} is true.
   */
  PlannedFileChange plan(
      Project project, List<InitGateOption> gates, QualityPreset preset, boolean approved);

  /**
   * Returns the gates of {@code gateIds} whose tool has no version for the project's runtime JDK.
   * The default omits nothing.
   */
  default List<GateOmission> omissions(final Project project, final List<String> gateIds) {
    return List.of();
  }

  /**
   * Returns the gates of {@code gateIds} whose version pinned in the build file does not run on the
   * project's runtime JDK, with that version's JDK range. The default reports none.
   */
  default List<GateOmission> unsupportedPins(final Project project, final List<String> gateIds) {
    return List.of();
  }

  /** Returns the runtime JDK that runs the project's build, when it can be read. */
  default Optional<Integer> runtimeJdk(final Project project) {
    return Optional.empty();
  }

  /** Restores the build file to its content before {@code change}. */
  void rollback(BuildFileChange change);
}
