package dev.ward.application.init;

import dev.ward.domain.init.ArchitectureEngine;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;

/**
 * Decides what the external architecture tool generates for a selection. Under the {@code ward}
 * engine the tool keeps only the strict extras, because the engine checks the layers; Swift has no
 * extras, so under the engine it generates nothing. Planning, applying and gate selection all read
 * this one decision.
 */
final class ArchitectureToolPlan {

  private ArchitectureToolPlan() {}

  /** Whether the external tool generates an architecture test for {@code selection}. */
  static boolean generatesTool(final Project project, final InitSelection selection) {
    return selection.architectureEngine() != ArchitectureEngine.WARD
        || wardLeavesExtras(project, selection);
  }

  /**
   * Whether {@code ward} leaves strict extras to the external tool, so the tool is still needed.
   */
  static boolean wardLeavesExtras(final Project project, final InitSelection selection) {
    return selection.architectureEngine() == ArchitectureEngine.WARD
        && extras(selection).any()
        && project.toolchain() != Toolchain.SWIFT;
  }

  /** The style the tool writes: {@code null} when only the extras remain. */
  static String style(final InitSelection selection) {
    if (selection.architectureEngine() == ArchitectureEngine.WARD) {
      return null;
    }
    return selection.architecture() == null ? "layered" : selection.architecture();
  }

  /** The strict extras of the selection's preset; none without a preset. */
  static QualityPreset.ArchitectureExtras extras(final InitSelection selection) {
    return selection.preset() == null
        ? new QualityPreset.ArchitectureExtras(false, false, false)
        : selection.preset().rules().architectureExtras();
  }
}
