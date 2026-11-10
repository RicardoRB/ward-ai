package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Turns the references of a project's summaries into edges between its files. */
@FunctionalInterface
public interface DependencyResolver {

  /**
   * Resolves the references of every summary. A reference that matches no file yields no edge.
   *
   * @param summaries the summaries of one run, in any order
   * @return the resolved edges
   */
  List<DependencyEdge> resolve(List<FileSummary> summaries);

  /**
   * Returns the project-root-relative files that a manifest declares as entry points, such as a
   * package's main or exports. A resolver with no manifests declares none.
   *
   * @return the entry files, empty by default
   */
  default Set<String> entryFiles() {
    return Set.of();
  }

  /**
   * Returns the module that owns the project-root-relative file {@code path}, when the resolver
   * knows the modules of the project. A resolver with no module layout knows none.
   *
   * @param path the project-root-relative path with {@code /} separators
   * @return the module name, or empty when the module of the file is unknown
   */
  default Optional<String> moduleOf(final String path) {
    return Optional.empty();
  }
}
