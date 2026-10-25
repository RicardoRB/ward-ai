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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Doctor checks the Node tool chain, not Maven or Gradle, for TypeScript and JavaScript. */
class DoctorNodeTest {
  private static final String NODE = "node";
  private static final String PACKAGE_JSON = "package.json";

  @TempDir Path root;

  @Test
  void pnpmProjectWithNodeAndPnpmIsOk() throws IOException {
    Files.writeString(root.resolve(PACKAGE_JSON), "{}");
    Files.writeString(root.resolve("pnpm-lock.yaml"), "");

    final List<DoctorService.Finding> findings = diagnose(NODE, "pnpm");

    assertThat(status(findings, NODE)).isEqualTo(DoctorService.Status.OK);
    assertThat(status(findings, "pnpm")).isEqualTo(DoctorService.Status.OK);
    assertThat(names(findings)).doesNotContain("maven", "gradle");
  }

  @Test
  void missingNodeIsAnError() throws IOException {
    Files.writeString(root.resolve(PACKAGE_JSON), "{}");

    final List<DoctorService.Finding> findings = diagnose("npm");

    assertThat(status(findings, NODE)).isEqualTo(DoctorService.Status.ERROR);
    assertThat(findings)
        .filteredOn(finding -> NODE.equals(finding.name()))
        .extracting(DoctorService.Finding::message)
        .containsExactly("node is required on PATH.");
    assertThat(status(findings, "npm")).isEqualTo(DoctorService.Status.OK);
  }

  @Test
  void nodeTwentyDeclaredWarnsWithoutFailing() throws IOException {
    Files.writeString(root.resolve(PACKAGE_JSON), "{}");
    Files.writeString(root.resolve(".nvmrc"), "20");

    final List<DoctorService.Finding> findings = diagnose(NODE, "npm");

    assertThat(status(findings, "node-version")).isEqualTo(DoctorService.Status.WARNING);
    assertThat(findings)
        .filteredOn(finding -> "node-version".equals(finding.name()))
        .extracting(DoctorService.Finding::message)
        .singleElement(InstanceOfAssertFactories.STRING)
        .contains("Node 20", "Node 22 and 24", "may not work");
    assertThat(status(findings, NODE)).isEqualTo(DoctorService.Status.OK);
    assertThat(status(findings, "npm")).isEqualTo(DoctorService.Status.OK);
  }

  @Test
  void supportedNodeVersionDoesNotWarn() throws IOException {
    Files.writeString(root.resolve(PACKAGE_JSON), "{}");
    Files.writeString(root.resolve(".nvmrc"), "24");

    final List<DoctorService.Finding> findings = diagnose(NODE, "npm");

    assertThat(names(findings)).doesNotContain("node-version");
  }

  @Test
  void bunProjectWithOldVersionFileDoesNotWarn() throws IOException {
    Files.writeString(root.resolve(PACKAGE_JSON), "{}");
    Files.writeString(root.resolve("bun.lock"), "");
    Files.writeString(root.resolve(".nvmrc"), "20");

    final List<DoctorService.Finding> findings = diagnose("bun");

    assertThat(names(findings)).doesNotContain("node-version");
  }

  @Test
  void bunProjectDoesNotNeedNode() throws IOException {
    Files.writeString(root.resolve(PACKAGE_JSON), "{}");
    Files.writeString(root.resolve("bun.lock"), "");

    final List<DoctorService.Finding> findings = diagnose("bun");

    assertThat(status(findings, "bun")).isEqualTo(DoctorService.Status.OK);
    assertThat(names(findings)).doesNotContain(NODE, "maven", "gradle");
  }

  private List<DoctorService.Finding> diagnose(final String... executables) {
    final EnvironmentInspection environment =
        projectRoot -> new EnvironmentFacts(Set.of(executables), Map.of());
    return new DoctorService(new ProjectDetector(new FileSystemProjectInspection()), environment)
        .diagnose(root);
  }

  private static DoctorService.Status status(
      final List<DoctorService.Finding> findings, final String name) {
    return findings.stream()
        .filter(finding -> name.equals(finding.name()))
        .findFirst()
        .orElseThrow()
        .status();
  }

  private static List<String> names(final List<DoctorService.Finding> findings) {
    return findings.stream().map(DoctorService.Finding::name).toList();
  }
}
