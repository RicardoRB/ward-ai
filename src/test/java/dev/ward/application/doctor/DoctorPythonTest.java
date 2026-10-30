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

/** Doctor checks the Python tool chain, not Maven or Gradle, for Python projects. */
class DoctorPythonTest {
  private static final String PYPROJECT = "pyproject.toml";
  private static final String PROJECT_ONLY = "[project]\nname = \"app\"\n";

  @TempDir Path root;

  @Test
  void uvProjectWithPythonAndUvIsOk() throws IOException {
    Files.writeString(root.resolve(PYPROJECT), PROJECT_ONLY);
    Files.writeString(root.resolve("uv.lock"), "");

    final List<DoctorService.Finding> findings = diagnose("python3", "uv");

    assertThat(finding(findings, "python").status()).isEqualTo(DoctorService.Status.OK);
    assertThat(finding(findings, "uv").status()).isEqualTo(DoctorService.Status.OK);
    assertThat(findings).extracting(DoctorService.Finding::name).doesNotContain("maven", "gradle");
  }

  @Test
  void poetryWithoutExecutableIsAnErrorNamingPoetry() throws IOException {
    Files.writeString(root.resolve(PYPROJECT), "[tool.poetry]\nname = \"app\"\n");

    final List<DoctorService.Finding> findings = diagnose("python3");

    assertThat(finding(findings, "poetry").status()).isEqualTo(DoctorService.Status.ERROR);
    assertThat(finding(findings, "poetry").message()).isEqualTo("poetry is required on PATH.");
  }

  @Test
  void uvWithoutPythonIsAWarning() throws IOException {
    Files.writeString(root.resolve(PYPROJECT), PROJECT_ONLY);
    Files.writeString(root.resolve("uv.lock"), "");

    final List<DoctorService.Finding> findings = diagnose("uv");

    assertThat(finding(findings, "python").status()).isEqualTo(DoctorService.Status.WARNING);
    assertThat(finding(findings, "uv").status()).isEqualTo(DoctorService.Status.OK);
  }

  @Test
  void pipWithoutPythonIsAnError() throws IOException {
    Files.writeString(root.resolve(PYPROJECT), PROJECT_ONLY);

    final List<DoctorService.Finding> findings = diagnose();

    assertThat(finding(findings, "python").status()).isEqualTo(DoctorService.Status.ERROR);
  }

  @Test
  void pipWithoutVirtualEnvironmentWarnsWithHowToCreateOne() throws IOException {
    Files.writeString(root.resolve(PYPROJECT), PROJECT_ONLY);

    final List<DoctorService.Finding> findings = diagnose("python3");

    assertThat(finding(findings, "venv").status()).isEqualTo(DoctorService.Status.WARNING);
    assertThat(finding(findings, "venv").message()).contains("python3 -m venv .venv");
  }

  @Test
  void pipWithVirtualEnvironmentIsOk() throws IOException {
    Files.writeString(root.resolve(PYPROJECT), PROJECT_ONLY);
    Files.createDirectories(root.resolve(".venv"));

    final List<DoctorService.Finding> findings = diagnose("python3");

    assertThat(finding(findings, "venv").status()).isEqualTo(DoctorService.Status.OK);
  }

  @Test
  void pdmWarnsThatWardDoesNotSupportIt() throws IOException {
    Files.writeString(root.resolve(PYPROJECT), PROJECT_ONLY);
    Files.writeString(root.resolve("pdm.lock"), "");

    final List<DoctorService.Finding> findings = diagnose("python3");

    assertThat(finding(findings, "pdm").status()).isEqualTo(DoctorService.Status.WARNING);
    assertThat(finding(findings, "pdm").message()).isEqualTo("Ward does not support PDM yet.");
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
