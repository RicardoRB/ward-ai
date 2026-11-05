package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.FlowSite;
import dev.ward.analysis.domain.ir.FlowStep;
import java.util.List;
import java.util.Objects;

/**
 * A value from a source that reaches a sink argument.
 *
 * @param sink the sink call
 * @param source the name of the source that produced the value
 * @param path the steps from the source to the sink, in order; the last step is the sink
 */
public record TaintFinding(FlowSite sink, String source, List<FlowStep> path) {

  /**
   * Copies the path.
   *
   * @throws NullPointerException if a field is missing
   */
  public TaintFinding {
    Objects.requireNonNull(sink, "sink");
    Objects.requireNonNull(source, "source");
    path = List.copyOf(Objects.requireNonNull(path, "path"));
  }
}
