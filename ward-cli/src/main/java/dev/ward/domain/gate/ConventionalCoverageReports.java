package dev.ward.domain.gate;

import dev.ward.domain.project.BuildTool;
import java.util.Map;
import java.util.Optional;

/**
 * Where each build tool's coverage tool writes its per-line report by default, relative to a
 * project or module directory. The paths are what JaCoCo's Maven and Gradle plugins, Vitest and
 * Jest, and pytest-cov write when asked for XML or lcov output.
 */
public final class ConventionalCoverageReports {
  private static final String NODE = "coverage/lcov.info";
  private static final String PYTHON = "coverage.lcov";
  private static final Map<BuildTool, String> REPORTS =
      Map.ofEntries(
          Map.entry(BuildTool.MAVEN, "target/site/jacoco/jacoco.xml"),
          Map.entry(BuildTool.GRADLE, "build/reports/jacoco/test/jacocoTestReport.xml"),
          Map.entry(BuildTool.NPM, NODE),
          Map.entry(BuildTool.PNPM, NODE),
          Map.entry(BuildTool.YARN, NODE),
          Map.entry(BuildTool.BUN, NODE),
          Map.entry(BuildTool.UV, PYTHON),
          Map.entry(BuildTool.POETRY, PYTHON),
          Map.entry(BuildTool.PDM, PYTHON),
          Map.entry(BuildTool.HATCH, PYTHON),
          Map.entry(BuildTool.PIP, PYTHON));

  private ConventionalCoverageReports() {}

  /**
   * Returns the default report path of {@code tool}, or empty when it has none: Swift and others.
   */
  public static Optional<String> of(final BuildTool tool) {
    return Optional.ofNullable(REPORTS.get(tool));
  }
}
