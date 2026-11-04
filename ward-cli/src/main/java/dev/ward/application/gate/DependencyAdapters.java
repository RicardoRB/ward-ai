package dev.ward.application.gate;

import dev.ward.analysis.domain.port.DependencySupport;
import java.nio.file.Path;
import java.util.List;

/**
 * Port that builds the dependency adapters of one project. Resolution needs the project's source
 * roots, which are read from the project itself, so the adapters are built per project.
 */
@FunctionalInterface
public interface DependencyAdapters {

  /**
   * Returns the adapters of the languages whose layout the project at {@code projectRoot} has. A
   * language without one contributes no edges.
   *
   * @param projectRoot the root of the project being analyzed
   * @return the adapters of the project
   */
  List<DependencySupport> forProject(Path projectRoot);
}
