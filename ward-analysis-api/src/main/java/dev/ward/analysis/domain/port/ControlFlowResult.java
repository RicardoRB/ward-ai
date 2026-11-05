package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import java.util.List;
import java.util.Objects;

/**
 * The control-flow graphs of one source unit and the bodies that could not be lowered.
 *
 * @param graphs one graph per body that was lowered
 * @param failures one finding per body that could not be lowered, copied on construction
 */
public record ControlFlowResult(List<ControlFlowGraph> graphs, List<AnalysisFinding> failures) {

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if a list is missing or holds a null element
   */
  public ControlFlowResult {
    graphs = List.copyOf(Objects.requireNonNull(graphs, "graphs"));
    failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
  }
}
