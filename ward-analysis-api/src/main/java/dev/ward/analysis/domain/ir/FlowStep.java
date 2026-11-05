package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * One step of a path that a finding reports, such as its source, a propagating assignment or its
 * sink.
 *
 * @param span where the step is in the source
 * @param message what happens at the step
 * @param file the project-root-relative file of the step, or {@code null} when the step is in the
 *     file of the finding it belongs to
 */
public record FlowStep(Span span, String message, String file) {

  /**
   * Creates a step in the file of the finding it belongs to.
   *
   * @throws NullPointerException if a field is missing
   */
  public FlowStep(final Span span, final String message) {
    this(span, message, null);
  }

  /**
   * Validates the fields.
   *
   * @throws NullPointerException if a required field is missing
   */
  public FlowStep {
    Objects.requireNonNull(span, "span");
    Objects.requireNonNull(message, "message");
  }
}
