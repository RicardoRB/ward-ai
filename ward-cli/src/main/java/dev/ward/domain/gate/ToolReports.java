package dev.ward.domain.gate;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;

/** Report locations that generated gate commands write and finding extraction reads. */
public final class ToolReports {
  private ToolReports() {}

  /** Returns the project-relative path of PIT's XML mutation report. */
  public static String pitMutations(final Project project) {
    return project.buildTool() == BuildTool.GRADLE
        ? "build/reports/pitest/mutations.xml"
        : "target/pit-reports/mutations.xml";
  }
}
