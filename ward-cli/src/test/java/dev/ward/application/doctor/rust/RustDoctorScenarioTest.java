package dev.ward.application.doctor.rust;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.doctor.DoctorService;
import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.FixedProjectInspection;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The doctor scenario of story R1. */
class RustDoctorScenarioTest {
  private static final Path ROOT = Path.of("app");
  private static final Project RUST =
      new Project(ROOT, Language.RUST, BuildTool.CARGO, Framework.NONE);

  @Test
  @DisplayName("a missing toolchain")
  void aMissingToolchain() {
    final List<Finding> findings = diagnose();

    assertThat(findings)
        .anySatisfy(
            finding -> {
              assertThat(finding.name()).isEqualTo("cargo");
              assertThat(finding.status()).isEqualTo(Status.ERROR);
              assertThat(finding.message()).contains("cargo is required", "rustup");
            }
        );
  }

  @Test
  void cargoOnThePathIsAvailable() {
    assertThat(diagnose("cargo"))
        .contains(new Finding("cargo", Status.OK, "cargo available."));
  }

  private static List<Finding> diagnose(final String... executables) {
    return new DoctorService(
        new ProjectDetector(new FixedProjectInspection(RUST)),
        new FakeEnvironmentInspection(executables)
    )
        .diagnose(ROOT);
  }
}
