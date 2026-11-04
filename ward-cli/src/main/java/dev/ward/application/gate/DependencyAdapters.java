package dev.ward.application.gate;

import dev.ward.analysis.domain.port.DependencySupport;
import java.nio.file.Path;
import java.util.List;

/** Port that builds the dependency adapters of the languages that have them for one project. */
@FunctionalInterface
public interface DependencyAdapters {

  /**
   * Returns the adapters for the project at {@code projectRoot}, read from its layout. A language
   * without an adapter for this project contributes no edges.
   */
  List<DependencySupport> forProject(Path projectRoot);
}
