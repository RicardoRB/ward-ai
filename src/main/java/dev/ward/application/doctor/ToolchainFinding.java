package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.project.Project;
import java.util.List;

/** Reports the build tool findings for projects of one toolchain. */
@FunctionalInterface
interface ToolchainFinding {

  /**
   * Returns the findings for {@code detected}, given what is installed on this machine and what
   * {@code context} reads from the project.
   */
  List<Finding> findings(Project detected, EnvironmentFacts facts, ToolchainContext context);
}
