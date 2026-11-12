package dev.ward.domain.config;

import java.util.List;

/**
 * The options of a {@code coverage} check beside its line minimum.
 *
 * @param reports the project-relative coverage report paths; empty to read the conventional report
 *     of each build tool
 * @param minimumBranches the least branch coverage in percent, or {@code null} to check none
 */
public record CoverageOptions(List<String> reports, Integer minimumBranches) {

  /** Copies the report paths. */
  public CoverageOptions {
    reports = List.copyOf(reports);
  }
}
