package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.DependencySupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Builds the dependency adapters of one project. The layout a resolver needs, such as a Node
 * project's workspace packages or a Java project's source roots, depends on the project root and
 * the files of the run, so the adapters are built per run rather than once.
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

  /**
   * Returns the adapters for a run whose {@code ward} check may name a Java classpath file.
   *
   * @param projectRoot the project root the units are read from
   * @param units the source units of the run, in any language
   * @param javaClasspath the project-relative classpath file that enables Java binding resolution,
   *     when the check sets one
   * @return the dependency supports to resolve the run with
   */
  default List<DependencySupport> forProject(
      final Path projectRoot, final List<SourceUnit> units, final Optional<String> javaClasspath) {
    return forProject(projectRoot, units);
  }
}
