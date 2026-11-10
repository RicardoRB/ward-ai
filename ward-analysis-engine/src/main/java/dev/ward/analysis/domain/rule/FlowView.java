package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.List;

/**
 * The control-flow graphs of one source unit, for a rule that reads data flow. A body that could
 * not be lowered has no graph here.
 *
 * @param graphs the graphs of the unit, in source order
 */
public record FlowView(List<ControlFlowGraph> graphs) {

  /** The view of a rule that reads no graphs. */
  public static final FlowView NONE = new FlowView(List.of());

  /**
   * Copies the graphs.
   *
   * @throws NullPointerException if the list is missing
   */
  public FlowView {
    graphs = List.copyOf(graphs);
  }
}
