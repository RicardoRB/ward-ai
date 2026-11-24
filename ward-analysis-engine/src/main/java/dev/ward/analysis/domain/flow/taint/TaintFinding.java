package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.Confidence;
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
 * @param assumptions why the flow is not proven, sorted; empty when every step was resolved
 */
public record TaintFinding(
    FlowSite sink,
    String source,
    List<FlowStep> path,
    List<String> assumptions
) {
  /**
   * Copies the path and the assumptions.
   *
   * @throws NullPointerException if a field is missing
   */
  public TaintFinding {
    Objects.requireNonNull(sink, "sink");
    Objects.requireNonNull(source, "source");
    path = List.copyOf(Objects.requireNonNull(path, "path"));
    assumptions = List.copyOf(Objects.requireNonNull(assumptions, "assumptions"));
  }

  /**
   * Creates a finding whose flow is proven.
   *
   * @param sink the sink call
   * @param source the name of the source that produced the value
   * @param path the steps from the source to the sink, in order
   */
  public TaintFinding(final FlowSite sink, final String source, final List<FlowStep> path) {
    this(sink, source, path, List.of());
  }

  /**
   * Returns how sure the finding is: proven when the flow crosses no assumed step.
   *
   * @return the confidence of the finding
   */
  public Confidence confidence() {
    return assumptions.isEmpty() ? Confidence.PROVEN : Confidence.POSSIBLE;
  }
}
