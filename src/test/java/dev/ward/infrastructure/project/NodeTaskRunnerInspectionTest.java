package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Turborepo detection from the workspace root's turbo.json. */
class NodeTaskRunnerInspectionTest {
  private static final String PACKAGE_JSON = "package.json";

  @TempDir Path dir;

  @Test
  void workspaceRootWithTurboJsonIsTurborepo() throws IOException {
    write(PACKAGE_JSON, "{\"workspaces\": [\"packages/*\"]}");
    write("turbo.json", "{}");

    assertThat(detect().taskRunner()).isEqualTo(TaskRunner.TURBO);
  }

  @Test
  void memberOfTurboRootResolvesToTheRoot() throws IOException {
    write(PACKAGE_JSON, "{\"workspaces\": [\"packages/*\"]}");
    write("turbo.json", "{}");
    final Path member = Files.createDirectories(dir.resolve("packages/app"));
    Files.writeString(member.resolve(PACKAGE_JSON), "{}");

    final Project project = new FileSystemProjectInspection().detect(member).orElseThrow();

    assertThat(project.root()).isEqualTo(dir);
    assertThat(project.taskRunner()).isEqualTo(TaskRunner.TURBO);
  }

  @Test
  void workspaceWithoutTurboJsonHasNoTaskRunner() throws IOException {
    write(PACKAGE_JSON, "{\"workspaces\": [\"packages/*\"]}");

    assertThat(detect().taskRunner()).isEqualTo(TaskRunner.NONE);
  }

  private Project detect() {
    return FileSystemProjectInspection.projectAt(dir).orElseThrow();
  }

  private void write(final String name, final String content) throws IOException {
    Files.writeString(dir.resolve(name), content);
  }
}
