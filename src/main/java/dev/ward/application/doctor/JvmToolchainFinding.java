package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Locale;

/** Checks the Maven or Gradle wrapper, or the system build tool when no wrapper exists. */
final class JvmToolchainFinding implements ToolchainFinding {

  @Override
  public List<Finding> findings(final Project detected, final EnvironmentFacts facts) {
    final String toolName = detected.buildTool().displayName();
    final String wrapperName =
        detected.buildTool() == BuildTool.GRADLE ? "Gradle Wrapper" : "Maven Wrapper";
    final String executable = detected.buildTool() == BuildTool.GRADLE ? "gradle" : "mvn";
    final String wrapperFlag =
        detected.buildTool() == BuildTool.GRADLE
            ? "gradleWrapperAvailable"
            : "mavenWrapperAvailable";
    final boolean wrapper = Boolean.parseBoolean(facts.values().get(wrapperFlag));
    final String toolMessage =
        wrapper ? wrapperName + " available." : "System " + toolName + " is required.";
    return List.of(
        new Finding(
            toolName.toLowerCase(Locale.ROOT),
            wrapper || facts.hasExecutable(executable) ? Status.OK : Status.ERROR,
            toolMessage));
  }
}
