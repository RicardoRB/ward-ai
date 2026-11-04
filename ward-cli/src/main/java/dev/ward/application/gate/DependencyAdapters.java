package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds the dependency adapters of one project. The layout a resolver needs, such as a Node
 * project's workspace packages or TypeScript configs, depends on the project root and the files of
 * the run, so the adapters are built per run rather than once.
 */
@FunctionalInterface
public interface DependencyAdapters {

  /** Adapters that contribute no edges, for a build with no dependency language. */
  static DependencyAdapters none() {
    return (projectRoot, units) -> List.of();
  }

  /**
   * Returns the adapters of the languages in {@code units} that resolve dependencies.
   *
   * @param projectRoot the project root the units are read from
   * @param units the source units of the run, in any language
   * @return the dependency supports to resolve the run with
   */
  List<DependencySupport> forProject(Path projectRoot, List<SourceUnit> units);
}
