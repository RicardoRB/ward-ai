package dev.ward.domain.gate;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;

/** Report locations that generated gate commands write and finding extraction reads. */
public final class ToolReports {
  private ToolReports() {}

  /**
   * Returns {@code .ward/reports} for Node and Python projects, {@code build} for Gradle projects
   * and {@code target} otherwise.
   */
  public static String buildDirectory(final Project project) {
    if (project.toolchain() == Toolchain.NODE || project.toolchain() == Toolchain.PYTHON) {
      return ".ward/reports";
    }
    return project.buildTool() == BuildTool.GRADLE ? "build" : "target";
  }

  /** Returns the project-relative path of PIT's XML mutation report. */
  public static String pitMutations(final Project project) {
    return project.buildTool() == BuildTool.GRADLE
        ? "build/reports/pitest/mutations.xml"
        : "target/pit-reports/mutations.xml";
  }

  /** Returns the project-relative path of the gitleaks SARIF report. */
  public static String gitleaksSarif(final Project project) {
    return buildDirectory(project) + "/gitleaks.sarif";
  }
}
