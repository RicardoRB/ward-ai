package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.Span;
import java.util.Objects;

/**
 * One place where a rule matched.
 *
 * @param span where the match is, or {@code null} when the rule reports no position
 * @param message the human-readable message of the finding
 */
public record RuleMatch(Span span, String message) {

  /**
   * Validates the message.
   *
   * @throws NullPointerException if the message is missing
   */
  public RuleMatch {
    Objects.requireNonNull(message, "message");
  }
}
