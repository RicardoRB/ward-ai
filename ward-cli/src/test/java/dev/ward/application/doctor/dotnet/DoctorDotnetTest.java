package dev.ward.application.doctor.dotnet;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.doctor.DoctorPorts;
import dev.ward.application.doctor.DoctorService;
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
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Doctor names the .NET SDK and what is missing, and never Maven or Gradle. */
class DoctorDotnetTest {
  private static final Path ROOT = Path.of("app");

  @Test
  @DisplayName("a missing toolchain")
  void aMissingToolchain() {
    final List<Finding> findings = diagnose();

    assertThat(findings)
        .anySatisfy(
            finding -> {
              assertThat(finding.name()).isEqualTo("dotnet");
              assertThat(finding.status()).isEqualTo(Status.ERROR);
              assertThat(finding.message())
                  .contains("dotnet is required on PATH")
                  .contains("https://dotnet.microsoft.com/download");
            }
        );
    assertThat(findings).extracting(Finding::name).doesNotContain("maven", "gradle");
  }

  @Test
  @DisplayName("an installed toolchain")
  void anInstalledToolchain() {
    assertThat(diagnose("dotnet")).contains(new Finding("dotnet", Status.OK, "dotnet available."));
  }

  private static List<Finding> diagnose(final String... executables) {
    final Project project = new Project(ROOT, Language.CSHARP, BuildTool.DOTNET, Framework.NONE);
    return new DoctorService(
        new ProjectDetector(new FixedProjectInspection(project)),
        new FakeEnvironmentInspection(executables),
        new DoctorPorts(null, null, null, null),
        Set.of()
    )
        .diagnose(ROOT);
  }
}
