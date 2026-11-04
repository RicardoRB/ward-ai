package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.FileSummary;
import java.util.List;

/**
 * The summaries of the files around the one a rule checks: the files it directly depends on, and
 * the files that directly depend on it, with the edges that lead from it. A rule reads them through
 * {@link CompiledRule#matches(dev.ward.analysis.domain.ir.SyntaxNode, DependencyView)}.
 *
 * @param dependencies the summaries of the files it imports, sorted by path
 * @param dependents the summaries of the files that import it, sorted by path
 * @param outgoing the resolved edges from the file to each file it imports, with their references
 */
public record DependencyView(
    List<FileSummary> dependencies, List<FileSummary> dependents, List<DependencyEdge> outgoing) {

  /** The view of a rule that reads no dependencies. */
  public static final DependencyView NONE = new DependencyView(List.of(), List.of(), List.of());

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if a list is missing
   */
  public DependencyView {
    dependencies = List.copyOf(dependencies);
    dependents = List.copyOf(dependents);
    outgoing = List.copyOf(outgoing);
  }
}
