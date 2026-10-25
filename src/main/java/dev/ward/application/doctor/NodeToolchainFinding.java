package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.NodeSupport;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;

/** Checks node and the package manager's executable on PATH; Bun runs JavaScript itself. */
final class NodeToolchainFinding implements ToolchainFinding {

  @Override
  public List<Finding> findings(final Project detected, final EnvironmentFacts facts) {
    if (detected.buildTool() == BuildTool.BUN) {
      return List.of(executableFinding(detected.buildExecutable(), facts));
    }
    final List<Finding> findings =
        new ArrayList<>(
            List.of(
                executableFinding("node", facts),
                executableFinding(detected.buildExecutable(), facts)));
    NodeSupport.warning(detected)
        .map(message -> new Finding("node-version", Status.WARNING, message))
        .ifPresent(findings::add);
    return findings;
  }

  private static Finding executableFinding(final String executable, final EnvironmentFacts facts) {
    return facts.hasExecutable(executable)
        ? new Finding(executable, Status.OK, executable + " available.")
        : new Finding(executable, Status.ERROR, executable + " is required on PATH.");
  }
}
