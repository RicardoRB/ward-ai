package dev.ward.domain.config;

import java.util.List;

/**
 * The options of a {@code crap} check.
 *
 * @param reports the project-relative coverage report paths; empty to read the conventional report
 *     of each build tool
 * @param max the largest CRAP score a function may have
 */
public record CrapOptions(List<String> reports, int max) {

  /** The largest score of the original CRAP metric, used when a check sets no {@code max}. */
  public static final int DEFAULT_MAX = 30;

  /** Copies the report paths. */
  public CrapOptions {
    reports = List.copyOf(reports);
  }

  /** Returns the options of a check that sets neither {@code report} nor {@code max}. */
  public static CrapOptions defaults() {
    return new CrapOptions(List.of(), DEFAULT_MAX);
  }
}
