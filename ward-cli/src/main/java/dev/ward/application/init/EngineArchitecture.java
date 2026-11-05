package dev.ward.application.init;

import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.domain.init.ArchitectureEngine;
import dev.ward.domain.init.ArchitectureGates;
import dev.ward.domain.init.ArchitectureTestGeneration;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.project.Project;
import java.util.Collections;
import java.util.List;

/**
 * The architecture tables the {@code ward} engine writes into {@code ward.toml} for a selection:
 * the layer boundaries, the source roots whose slices must not form cycles, and the module
 * directories that must stay independent. Every list is empty unless the ward engine checks it.
 *
 * @param layers the layer boundaries of the style
 * @param cycleRoots the project-relative roots of {@code [checks.ward.cycles]}
 * @param independence the module directories of {@code [[checks.ward.independence]]}
 */
record EngineArchitecture(
    List<LayerBoundary> layers, List<String> cycleRoots, List<String> independence) {

  /** The tables of a selection that the ward engine does not check. */
  static final EngineArchitecture NONE = new EngineArchitecture(List.of(), List.of(), List.of());

  /** Whether the ward engine writes any of the tables. */
  boolean any() {
    return !layers.isEmpty() || !cycleRoots.isEmpty() || !independence.isEmpty();
  }

  /** Copies the lists. */
  EngineArchitecture {
    layers = List.copyOf(layers);
    cycleRoots = List.copyOf(cycleRoots);
    independence = List.copyOf(independence);
  }

  /**
   * Returns the tables of {@code selection} for {@code project}, asking {@code tests} for the
   * source roots and modules of its toolchain.
   */
  static EngineArchitecture of(
      final InitSelection selection,
      final Project project,
      final ArchitectureTestGeneration tests) {
    final List<LayerBoundary> layers = EngineLayers.of(selection, project);
    if (!wantsCycles(selection)) {
      return new EngineArchitecture(layers, List.of(), List.of());
    }
    return new EngineArchitecture(
        layers,
        tests.cycleRoots(project),
        tests.independenceModules(project, EngineLayers.style(selection)));
  }

  /** Whether the ward engine owns the strict cycle check for the selection's architecture gates. */
  private static boolean wantsCycles(final InitSelection selection) {
    return selection.architectureEngine() == ArchitectureEngine.WARD
        && selection.preset() != null
        && selection.preset().rules().architectureExtras().cycles()
        && !Collections.disjoint(selection.gates(), ArchitectureGates.IDS);
  }
}
