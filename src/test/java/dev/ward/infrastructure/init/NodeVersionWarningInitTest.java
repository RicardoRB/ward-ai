package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import dev.ward.infrastructure.project.FileSystemPackageManifests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Node version warning init shows, which never changes the files it writes. */
class NodeVersionWarningInitTest {
  private static final String PRETTIER = "prettier";
  private static final String MANIFEST =
      """
      {
        "name": "shop",
        "devDependencies": {
          "typescript": "^5.9.3"
        }
      }
      """;

  @TempDir Path root;

  private final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

  @BeforeEach
  void manifest() throws IOException {
    Files.writeString(root.resolve("package.json"), MANIFEST);
  }

  @Test
  void oldNodeWarnsButWritesTheSameFiles() throws IOException {
    final BuildFileChange change = configurator().apply(project(BuildTool.NPM, "20"), gates());

    assertThat(change.warnings()).anyMatch(warning -> warning.contains("Node 20"));
    assertThat(Files.readString(root.resolve("package.json"))).contains("\"prettier\": \"3.9.10\"");
  }

  @Test
  void dryRunShowsTheOldNodeWarningWithoutChangingFiles() throws IOException {
    final PlannedFileChange plan =
        configurator().plan(project(BuildTool.NPM, "18"), gates(), null, false);

    assertThat(plan.details()).anyMatch(detail -> detail.contains("Node 18"));
    assertThat(executor.commands).isEmpty();
    assertThat(Files.readString(root.resolve("package.json"))).isEqualTo(MANIFEST);
  }

  @Test
  void supportedNodeVersionHasNoNodeWarning() {
    final BuildFileChange change = configurator().apply(project(BuildTool.NPM, "22"), gates());

    assertThat(change.warnings()).noneMatch(warning -> warning.contains("Ward supports"));
  }

  @Test
  void bunProjectWithOldVersionHasNoNodeWarning() {
    final BuildFileChange change = configurator().apply(project(BuildTool.BUN, "20"), gates());

    assertThat(change.warnings()).noneMatch(warning -> warning.contains("Ward supports"));
  }

  private NodeBuildToolConfigurator configurator() {
    return new NodeBuildToolConfigurator(new FileSystemPackageManifests(), executor);
  }

  private Project project(final BuildTool manager, final String node) {
    return new Project(
        root,
        Language.TYPESCRIPT,
        manager,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        node,
        TaskRunner.NONE,
        null);
  }

  private static List<InitGateOption> gates() {
    return List.of(
        new InitGateOption(PRETTIER, PRETTIER, List.of("npx", PRETTIER), true, "available"));
  }
}
