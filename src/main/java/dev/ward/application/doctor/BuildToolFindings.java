package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.util.List;
import java.util.Locale;

/** Checks the build tool chain of a project: the JVM build tool, or Node and its manager. */
final class BuildToolFindings {
  private BuildToolFindings() {}

  static List<Finding> of(final Project detected, final EnvironmentFacts facts) {
    if (detected.toolchain() == Toolchain.NODE) {
      return nodeFindings(detected, facts);
    }
    if (detected.toolchain() != Toolchain.JVM) {
      final String toolName = detected.buildTool().displayName();
      return List.of(
          new Finding(
              toolName.toLowerCase(Locale.ROOT),
              Status.WARNING,
              "Ward does not check the " + toolName + " toolchain yet."));
    }
    return List.of(jvmToolFinding(detected, facts));
  }

  private static List<Finding> nodeFindings(final Project detected, final EnvironmentFacts facts) {
    // Bun runs JavaScript itself, so only the other managers need node on PATH.
    return detected.buildTool() == BuildTool.BUN
        ? List.of(executableFinding(detected.buildExecutable(), facts))
        : List.of(
            executableFinding("node", facts), executableFinding(detected.buildExecutable(), facts));
  }

  private static Finding jvmToolFinding(final Project detected, final EnvironmentFacts facts) {
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
    return new Finding(
        toolName.toLowerCase(Locale.ROOT),
        wrapper || facts.hasExecutable(executable) ? Status.OK : Status.ERROR,
        toolMessage);
  }

  private static Finding executableFinding(final String executable, final EnvironmentFacts facts) {
    return facts.hasExecutable(executable)
        ? new Finding(executable, Status.OK, executable + " available.")
        : new Finding(executable, Status.ERROR, executable + " is required on PATH.");
  }
}
