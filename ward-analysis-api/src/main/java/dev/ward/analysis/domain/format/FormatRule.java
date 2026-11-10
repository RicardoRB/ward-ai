package dev.ward.analysis.domain.format;

import java.util.Objects;

/**
 * One parse-tree node in pre-order.
 *
 * @param kind the rule kind, such as a block or a call
 * @param line the line where the node starts, from 1
 */
public record FormatRule(String kind, int line) {

  /**
   * Checks the kind.
   *
   * @throws NullPointerException if the kind is missing
   */
  public FormatRule {
    Objects.requireNonNull(kind, "kind");
  }
}
