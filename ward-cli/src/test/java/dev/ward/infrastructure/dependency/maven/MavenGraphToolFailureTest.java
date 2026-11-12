package dev.ward.infrastructure.dependency.maven;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.InventoryFixtures;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MavenGraphToolFailureTest {
  private static final Path DIR = InventoryFixtures.fixture("maven-reactor").toAbsolutePath();

  private static Project project(final boolean wrapper) {
    return new Project(DIR, Language.JAVA, BuildTool.MAVEN, Framework.NONE, wrapper);
  }

  @Test
  void aFailingBuildNamesTheExecutableAndTheFirstErrorLine() {
    final FakeCommandExecutor executor =
        new FakeCommandExecutor(
            1, "[INFO] x\n[ERROR] Could not resolve dependencies for api\n", "");

    assertThatThrownBy(() -> new MavenGraphTool(executor, false).resolve(project(false)))
        .isInstanceOf(DependencyInventoryException.class)
        .hasMessageContaining("mvn")
        .hasMessageContaining("Could not resolve dependencies for api")
        .hasMessageNotContaining("-DoutputType");
  }

  @Test
  void aToolThatCannotStartSaysWhichExecutableWasNotFound() {
    final FakeCommandExecutor executor =
        new FakeCommandExecutor(
            new CommandResult(
                -1,
                "",
                "Could not start mvn: no such file",
                Duration.ZERO,
                "PROCESS_START_FAILURE"));

    assertThatThrownBy(() -> new MavenGraphTool(executor, false).resolve(project(false)))
        .hasMessageContaining("mvn was not found");
  }

  @Test
  void aTimeoutIsReported() {
    final FakeCommandExecutor executor =
        new FakeCommandExecutor(
            new CommandResult(-3, "", "Timed out", Duration.ZERO, "PROCESS_TIMEOUT"));

    assertThatThrownBy(() -> new MavenGraphTool(executor, false).resolve(project(false)))
        .hasMessageContaining("did not finish");
  }

  @Test
  void outputWithoutATreeIsAnError() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "[INFO] nothing\n", "");

    assertThatThrownBy(() -> new MavenGraphTool(executor, false).resolve(project(false)))
        .hasMessageContaining("no dependency tree");
  }
}
