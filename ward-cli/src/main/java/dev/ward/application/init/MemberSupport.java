package dev.ward.application.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.ScriptReplacement;
import dev.ward.domain.project.Project;
import java.util.List;

/** The member projects a toolchain writes beneath a multi-package root. */
interface MemberSupport {
  /** Returns the writer of the members {@code project} runs; the default has none. */
  default MemberWriter memberWriter(final InitProjectFiles files, final InitInputs inputs) {
    return MemberWriter.NONE;
  }

  /**
   * Lists the member changes of {@code project} and its workspace file, in the order they are
   * applied; the default has none.
   */
  default List<PlannedFileChange> planMembers(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final boolean approved,
      final MemberPlanning planning) {
    return List.of();
  }

  /** Returns the member scripts that differ from what init would write; empty by default. */
  default List<ScriptReplacement> scriptReplacements(
      final Project project, final QualityPreset preset, final InitInputs inputs) {
    return List.of();
  }
}
