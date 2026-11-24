package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.Confidence;
import dev.ward.analysis.domain.ir.FlowStep;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Objects;

/**
 * One place where a rule matched.
 *
 * @param span where the match is, or {@code null} when the rule reports no position
 * @param message the human-readable message of the finding
 * @param path the steps of the match's path in order; empty for a match without one
 * @param confidence how sure a data-flow match is, or {@code null} for a match without a flow
 * @param assumptions the steps of the flow that the analysis assumed rather than proved
 */
public record RuleMatch(
    Span span,
    String message,
    List<FlowStep> path,
    Confidence confidence,
    List<String> assumptions
) {
  /**
   * Creates a match without a confidence.
   *
   * @param span where the match is, or {@code null} when the rule reports no position
   * @param message the human-readable message of the finding
   * @param path the steps of the match's path in order
   */
  public RuleMatch(final Span span, final String message, final List<FlowStep> path) {
    this(span, message, path, null, List.of());
  }

  /**
   * Creates a match without a path.
   *
   * @param span where the match is, or {@code null} when the rule reports no position
   * @param message the human-readable message of the finding
   */
  public RuleMatch(final Span span, final String message) {
    this(span, message, List.of());
  }

  /**
   * Copies the path and validates the message.
   *
   * @throws NullPointerException if the message or path is missing
   */
  public RuleMatch {
    Objects.requireNonNull(message, "message");
    path = List.copyOf(Objects.requireNonNull(path, "path"));
    assumptions = List.copyOf(Objects.requireNonNull(assumptions, "assumptions"));
  }
}
