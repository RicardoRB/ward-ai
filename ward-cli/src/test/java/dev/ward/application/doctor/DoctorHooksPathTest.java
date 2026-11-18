package dev.ward.application.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.doctor.SystemEnvironmentInspection;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DoctorHooksPathTest {
  @TempDir Path root;

  @Test
  @DisplayName("a hooks path that points nowhere")
  void aHooksPathThatPointsNowhere() throws IOException {
    // Given core.hooksPath set to a folder that does not exist
    Files.writeString(root.resolve("pom.xml"), "<project/>");
    final EnvironmentInspection environment =
        new EnvironmentInspection() {
          @Override
          public EnvironmentFacts inspect(final Path projectRoot) {
            return new EnvironmentFacts(Set.of(), Map.of());
          }

          @Override
          public Optional<String> missingHooksPath(final Path projectRoot) {
            return Optional.of(".githooks");
          }
        };

    // When I run ward doctor
    final Finding finding =
        new DoctorService(new ProjectDetector(new FileSystemProjectInspection()), environment)
            .diagnose(root)
            .stream()
            .filter(found -> "git-hooks".equals(found.name()))
            .findFirst()
            .orElseThrow();

    // Then it reports that Git runs no hook and names the folder
    assertThat(finding.status()).isEqualTo(Status.WARNING);
    assertThat(finding.message()).contains("Git runs no hook", ".githooks");
  }

  @Test
  void theInspectionNamesAMissingFolderOnly() throws IOException {
    final FakeCommandExecutor git =
        new FakeCommandExecutor(
            new CommandResult(0, ".githooks\n", "", FakeCommandExecutor.DURATION),
            new CommandResult(0, root + "\n", "", FakeCommandExecutor.DURATION)
        );
    final SystemEnvironmentInspection inspection = new SystemEnvironmentInspection(git);

    assertThat(inspection.missingHooksPath(root)).contains(".githooks");
    Files.createDirectories(root.resolve(".githooks"));
    final FakeCommandExecutor again =
        new FakeCommandExecutor(
            new CommandResult(0, ".githooks\n", "", FakeCommandExecutor.DURATION),
            new CommandResult(0, root + "\n", "", FakeCommandExecutor.DURATION)
        );
    assertThat(new SystemEnvironmentInspection(again).missingHooksPath(root)).isEmpty();
  }
}
