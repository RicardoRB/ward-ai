package dev.ward.application.init;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.init.ArchitectureLayers;
import dev.ward.domain.init.ArchitectureTables;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.project.Project;
import java.util.List;

/**
 * The architecture tables the {@code ward} gate reads from {@code ward.toml} for a selection: the
 * layer boundary of the style, always; and, for a preset with the cycle check, the source roots
 * whose slices must not form cycles and the module directories that must stay independent.
 *
 * @param layers the layer boundaries of the style
 * @param cycleRoots the project-relative roots of {@code [checks.ward.cycles]}
 * @param independence the module directories of {@code [[checks.ward.independence]]}
 */
record WardArchitecture(
    List<LayerBoundary> layers, List<String> cycleRoots, List<String> independence) {

  /** The style init writes when none is chosen, as the planner and the wizard default to it. */
  private static final String DEFAULT_STYLE = "layered";

  /** The tables of a selection that writes none. */
  static final WardArchitecture NONE = new WardArchitecture(List.of(), List.of(), List.of());

  /** Whether the ward gate writes any table: it always writes the layers of the style. */
  boolean any() {
    return !layers.isEmpty() || !cycleRoots.isEmpty() || !independence.isEmpty();
  }

  /** Copies the lists. */
  WardArchitecture {
    layers = List.copyOf(layers);
    cycleRoots = List.copyOf(cycleRoots);
    independence = List.copyOf(independence);
  }

  /**
   * Returns the tables of {@code selection} for {@code project}, asking {@code tables} for the
   * source roots and modules of its toolchain.
   */
  static WardArchitecture of(
      final InitSelection selection, final Project project, final ArchitectureTables tables) {
    final String style = style(selection);
    final List<LayerBoundary> layers =
        List.of(ArchitectureLayers.forStyle(style, project.framework()));
    if (!wantsCycles(selection)) {
      return new WardArchitecture(layers, List.of(), List.of());
    }
    return new WardArchitecture(
        layers, tables.cycleRoots(project), tables.independenceModules(project, style));
  }

  /** The style the selection checks: the chosen one, or the default when none is chosen. */
  static String style(final InitSelection selection) {
    return selection.architecture() == null ? DEFAULT_STYLE : selection.architecture();
  }

  /** Whether the selection's preset checks cycles between slices. */
  private static boolean wantsCycles(final InitSelection selection) {
    return selection.preset() != null && selection.preset().rules().architectureCycles();
  }
}
