package dev.ward.domain.gate;

import dev.ward.domain.project.BuildTool;
import java.util.Optional;

/**
 * Where each build tool's coverage tool writes its per-line report by default, relative to a
 * project or module directory. The paths are what JaCoCo's Maven and Gradle plugins, Vitest and
 * Jest, and pytest-cov write when asked for XML or lcov output.
 */
public final class ConventionalCoverageReports {
  private static final String NODE = "coverage/lcov.info";
  private static final String PYTHON = "coverage.lcov";

  private ConventionalCoverageReports() {}

  /**
   * Returns the default report path of {@code tool}, or empty when it has none: Swift and others.
   */
  public static Optional<String> of(final BuildTool tool) {
    return switch (tool) {
      case MAVEN -> Optional.of("target/site/jacoco/jacoco.xml");
      case GRADLE -> Optional.of("build/reports/jacoco/test/jacocoTestReport.xml");
      case NPM, PNPM, YARN, BUN -> Optional.of(NODE);
      case UV, POETRY, PDM, HATCH, PIP -> Optional.of(PYTHON);
      default -> Optional.empty();
    };
  }
}
