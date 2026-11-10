package dev.ward.analysis.domain.rule;

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
 */
public record RuleMatch(Span span, String message, List<FlowStep> path) {

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
  }
}
