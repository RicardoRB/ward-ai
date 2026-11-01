package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.init.BuildFileChange;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Turborepo root gets a pinned turbo dev dependency, unless it already declares one. */
class TurborepoRootDependencyTest {
  private static final String MANIFEST = "{\n  \"name\": \"monorepo\"\n}\n";

  @TempDir Path root;

  private final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
  private final NodeBuildToolConfigurator configurator =
      new NodeBuildToolConfigurator(new FileSystemPackageManifests(), executor);

  @Test
  void turborepoRootWithoutTurboAddsThePinnedVersion() throws IOException {
    Files.writeString(root.resolve("package.json"), MANIFEST);

    final BuildFileChange change = configurator.apply(project(TaskRunner.TURBO), List.of());

    assertThat(change.tools()).containsExactly("turbo");
    assertThat(Files.readString(root.resolve("package.json")))
        .contains("\"turbo\": \"" + NodePackageCatalog.TURBO_VERSION + "\"");
  }

  @Test
  void turborepoRootThatDeclaresTurboKeepsItsVersion() throws IOException {
    Files.writeString(
        root.resolve("package.json"),
        "{\n  \"devDependencies\": {\n    \"turbo\": \"2.0.0\"\n  }\n}\n");

    final BuildFileChange change = configurator.apply(project(TaskRunner.TURBO), List.of());

    assertThat(change.tools()).isEmpty();
  }

  @Test
  void singleProjectGetsNoTurbo() throws IOException {
    Files.writeString(root.resolve("package.json"), MANIFEST);

    final BuildFileChange change = configurator.apply(project(TaskRunner.NONE), List.of());

    assertThat(change.tools()).doesNotContain("turbo");
  }

  private Project project(final TaskRunner runner) {
    return new Project(
        root,
        Language.TYPESCRIPT,
        BuildTool.NPM,
        Framework.NONE,
        false,
        false,
        root,
        Set.of(),
        "24",
        runner,
        null);
  }
}
