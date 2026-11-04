package dev.ward.application.gate;

import dev.ward.analysis.domain.port.DependencySupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Builds the dependency adapters of a project. A resolver reads the project's layout, such as its
 * source roots, so the adapters are built per project rather than once.
 */
@FunctionalInterface
public interface DependencyAdapters {

  /**
   * Returns the adapters of the languages that resolve edges, for the project at {@code
   * projectRoot}. A language without an adapter contributes no edges, so the list may be empty.
   *
   * @param projectRoot the project root
   * @param javaClasspath the project-relative classpath file that enables Java binding resolution,
   *     when the check sets one
   */
  List<DependencySupport> forProject(Path projectRoot, Optional<String> javaClasspath);
}
