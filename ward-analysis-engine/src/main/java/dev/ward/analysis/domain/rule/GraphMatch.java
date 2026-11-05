package dev.ward.analysis.domain.rule;

import java.util.Objects;

/**
 * One match of a graph rule.
 *
 * @param file the project-root-relative file the match sits in
 * @param match the match, with its span and path
 */
public record GraphMatch(String file, RuleMatch match) {

  /**
   * Validates the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public GraphMatch {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(match, "match");
  }
}
