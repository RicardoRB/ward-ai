package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import java.util.List;
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
}
