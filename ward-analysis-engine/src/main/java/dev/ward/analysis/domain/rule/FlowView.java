package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.List;

/**
 * The control-flow graphs of one source unit, for a rule that reads data flow. A body that could
 * not be lowered has no graph here.
 *
 * @param graphs the graphs of the unit, in source order
 * @param related the graphs of the files the unit depends on, directly or not, in path order; empty
 *     for a rule that does not read them
 * @param files the files the unit depends on that parsed, with their trees, in path order; empty
 *     for a rule that does not read them
 */
public record FlowView(
    List<ControlFlowGraph> graphs,
    List<ControlFlowGraph> related,
    List<RelatedFile> files
) {
  /** The view of a rule that reads no graphs. */
  public static final FlowView NONE = new FlowView(List.of());

  /** Creates a view with no graphs of other files. */
  public FlowView(final List<ControlFlowGraph> graphs) {
    this(graphs, List.of(), List.of());
  }

  /** Creates a view with the graphs of other files and none of their trees. */
  public FlowView(final List<ControlFlowGraph> graphs, final List<ControlFlowGraph> related) {
    this(graphs, related, List.of());
  }

  /**
   * Copies the graphs.
   *
   * @throws NullPointerException if the list is missing
   */
  public FlowView {
    graphs = List.copyOf(graphs);
    related = List.copyOf(related);
    files = List.copyOf(files);
  }
}
