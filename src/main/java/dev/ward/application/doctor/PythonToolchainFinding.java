package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.PythonSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Checks the Python interpreter, the package manager or virtual environment, and the version. */
final class PythonToolchainFinding implements ToolchainFinding {

  @Override
  public List<Finding> findings(
      final Project detected, final EnvironmentFacts facts, final ToolchainContext context) {
    final List<Finding> findings = new ArrayList<>();
    findings.add(interpreterFinding(detected, facts));
    managerFinding(detected, facts).ifPresent(findings::add);
    PythonSupport.incompatibility(detected)
        .map(message -> new Finding("python-version", Status.ERROR, message))
        .ifPresent(findings::add);
    return findings;
  }

  private static Finding interpreterFinding(final Project detected, final EnvironmentFacts facts) {
    if (facts.hasExecutable("python3") || facts.hasExecutable("python")) {
      return new Finding("python", Status.OK, "Python interpreter available.");
    }
    return detected.buildTool() == BuildTool.UV
        ? new Finding(
            "python", Status.WARNING, "python3 is not on PATH; uv installs its own interpreter.")
        : new Finding("python", Status.ERROR, "python3 or python is required on PATH.");
  }

  private static Optional<Finding> managerFinding(
      final Project detected, final EnvironmentFacts facts) {
    return switch (detected.buildTool()) {
      case UV, POETRY -> Optional.of(executableFinding(detected.buildExecutable(), facts));
      case PDM, HATCH -> Optional.of(unsupportedFinding(detected.buildTool()));
      case PIP -> Optional.of(venvFinding(detected));
      default -> Optional.empty();
    };
  }

  private static Finding executableFinding(final String executable, final EnvironmentFacts facts) {
    return facts.hasExecutable(executable)
        ? new Finding(executable, Status.OK, executable + " available.")
        : new Finding(executable, Status.ERROR, executable + " is required on PATH.");
  }

  private static Finding unsupportedFinding(final BuildTool tool) {
    return new Finding(
        tool.displayName().toLowerCase(Locale.ROOT),
        Status.WARNING,
        "Ward does not support " + tool.displayName() + " yet.");
  }

  private static Finding venvFinding(final Project detected) {
    return detected.wrapperAvailable()
        ? new Finding("venv", Status.OK, ".venv available.")
        : new Finding(
            "venv",
            Status.WARNING,
            "No .venv in the project root; create one with 'python3 -m venv .venv'.");
  }
}
