package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import java.util.List;

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
}
