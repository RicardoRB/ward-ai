package dev.ward.analysis.domain.rule;

import java.util.Objects;

/**
 * One place where a whole-project rule matched.
 *
 * @param path the project-relative path of the file that holds the match
 * @param match where in that file and what the finding says
 */
public record ProjectMatch(String path, RuleMatch match) {

  /**
   * Validates the parts.
   *
   * @throws NullPointerException if the path or match is missing
   */
  public ProjectMatch {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(match, "match");
  }
}
