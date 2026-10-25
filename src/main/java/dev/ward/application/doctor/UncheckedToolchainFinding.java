package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Locale;

/** Warns that Ward does not check the build tool's toolchain yet. */
final class UncheckedToolchainFinding implements ToolchainFinding {

  @Override
  public List<Finding> findings(
      final Project detected, final EnvironmentFacts facts, final ToolchainContext context) {
    final String toolName = detected.buildTool().displayName();
    return List.of(
        new Finding(
            toolName.toLowerCase(Locale.ROOT),
            Status.WARNING,
            "Ward does not check the " + toolName + " toolchain yet."));
  }
}
