package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * One step of a path that a finding reports, such as its source, a propagating assignment or its
 * sink.
 *
 * @param span where the step is in the source
 * @param message what happens at the step
 */
public record FlowStep(Span span, String message) {

  /**
   * Validates the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public FlowStep {
    Objects.requireNonNull(span, "span");
    Objects.requireNonNull(message, "message");
  }
}
