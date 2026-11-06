package dev.ward.application.doctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.FixedProjectInspection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Doctor checks the JVM build tool and never reports Maven for another toolchain. */
class DoctorToolchainTest {
  private static final Path ROOT = Path.of("app");

  @Test
  void unsupportedToolchainWarnsWithoutMentioningMaven() {
    final List<Finding> findings =
        diagnose(new Project(ROOT, Language.RUST, BuildTool.CARGO, Framework.NONE));

    assertThat(findings)
        .contains(
            new Finding("cargo", Status.WARNING, "Ward does not check the Cargo toolchain yet."));
    assertThat(findings).extracting(Finding::message).noneMatch(m -> m.contains("Maven"));
  }

  @Test
  void swiftProjectIsCheckedInsteadOfWarnedAsUnchecked() {
    final List<Finding> findings =
        diagnose(new Project(ROOT, Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE));

    assertThat(findings).extracting(Finding::name).contains("swift").doesNotContain("swiftpm");
    assertThat(findings).extracting(Finding::message).noneMatch(m -> m.contains("does not check"));
  }

  @Test
  void mavenWithoutWrapperOrExecutableIsAnError() {
    final List<Finding> findings =
        diagnose(new Project(ROOT, Language.JAVA, BuildTool.MAVEN, Framework.NONE));

    assertThat(findings).contains(new Finding("maven", Status.ERROR, "System Maven is required."));
  }

  @Test
  void missingTypescriptIsAnOptionalWarningOnNodeProjects() {
    final List<Finding> findings =
        diagnose(
            new Project(ROOT, Language.TYPESCRIPT, BuildTool.NPM, Framework.NONE), "node", "npm");

    assertThat(findings)
        .extracting(Finding::name, Finding::status)
        .contains(tuple("typescript-type-sidecar", Status.WARNING));
  }

  @Test
  void javaProjectsNeedNoCompilerForTypeFacts() {
    final List<Finding> findings =
        diagnose(
            new Project(ROOT, Language.JAVA, BuildTool.GRADLE, Framework.NONE), "gradle", "java");

    assertThat(findings).extracting(Finding::name).doesNotContain("java-type-sidecar");
  }

  private static List<Finding> diagnose(final Project project, final String... executables) {
    return new DoctorService(
            new ProjectDetector(new FixedProjectInspection(project)),
            new FakeEnvironmentInspection(executables))
        .diagnose(ROOT);
  }

  private static List<Finding> diagnose(final Project project) {
    return diagnose(project, new String[0]);
  }
}
