package dev.ward.application.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Doctor reports a Python project's target version only when Ward does not support it. */
class DoctorPythonVersionTest {
  private static final String PYPROJECT = "pyproject.toml";
  private static final String PROJECT_ONLY = "[project]\nname = \"app\"\n";

  @TempDir Path root;

  @Test
  void olderPythonVersionIsIncompatible() throws IOException {
    Files.writeString(root.resolve(PYPROJECT), PROJECT_ONLY);
    Files.writeString(root.resolve(".python-version"), "3.10\n");

    final List<DoctorService.Finding> findings = diagnose("python3");

    assertThat(finding(findings, "python-version").status()).isEqualTo(DoctorService.Status.ERROR);
    assertThat(finding(findings, "python-version").message())
        .contains("Python 3.10 is INCOMPATIBLE")
        .contains("3.11 to 3.14");
  }

  @Test
  void supportedPythonVersionPrintsNoVersionLine() throws IOException {
    Files.writeString(root.resolve(PYPROJECT), PROJECT_ONLY);

    final List<DoctorService.Finding> findings = diagnose("python3");

    assertThat(findings).extracting(DoctorService.Finding::name).doesNotContain("python-version");
  }

  private List<DoctorService.Finding> diagnose(final String... executables) {
    final EnvironmentInspection environment =
        projectRoot -> new EnvironmentFacts(Set.of(executables), Map.of());
    return new DoctorService(new ProjectDetector(new FileSystemProjectInspection()), environment)
        .diagnose(root);
  }

  private static DoctorService.Finding finding(
      final List<DoctorService.Finding> findings, final String name) {
    return findings.stream()
        .filter(finding -> name.equals(finding.name()))
        .findFirst()
        .orElseThrow();
  }
}
