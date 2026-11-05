package dev.ward.application.init;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.init.ArchitectureEngine;
import dev.ward.domain.init.ArchitectureGates;
import dev.ward.domain.init.ArchitectureLayers;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.project.Project;
import java.util.Collections;
import java.util.List;

/**
 * The layer boundaries the {@code ward} gate enforces for a selection. They exist only when the
 * ward engine checks an architecture style; under the external tool there are none.
 */
final class EngineLayers {
  /** The style init writes when none is chosen, as the planner and the wizard default to it. */
  private static final String DEFAULT_STYLE = "layered";

  private EngineLayers() {}

  /**
   * Returns the boundary of the selected style, or no boundary unless the ward engine checks it.
   */
  static List<LayerBoundary> of(final InitSelection selection, final Project project) {
    if (selection.architectureEngine() != ArchitectureEngine.WARD
        || Collections.disjoint(selection.gates(), ArchitectureGates.IDS)) {
      return List.of();
    }
    return List.of(ArchitectureLayers.forStyle(style(selection), project.framework()));
  }

  /** The style the selection checks: the chosen one, or the default when none is chosen. */
  static String style(final InitSelection selection) {
    return selection.architecture() == null ? DEFAULT_STYLE : selection.architecture();
  }
}
