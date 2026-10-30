package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** package.json edits, the install, and their rollback. */
class NodeBuildToolConfiguratorTest {
  private static final String MANIFEST =
      """
      {
        "name": "shop",
        "devDependencies": {
          "typescript": "^5.9.3"
        }
      }
      """;
  private static final String LOCK = "{\"lockfileVersion\": 3}\n";

  @TempDir Path root;

  private final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
  private final NodeBuildToolConfigurator configurator =
      new NodeBuildToolConfigurator(new FileSystemPackageManifests(), executor);

  @BeforeEach
  void manifest() throws IOException {
    Files.writeString(root.resolve("package.json"), MANIFEST);
  }

  @ParameterizedTest
  @CsvSource({"NPM, npm", "PNPM, pnpm", "YARN, yarn", "BUN, bun"})
  void installsWithThePackageManager(final BuildTool manager, final String executable) {
    final BuildFileChange change = configurator.apply(project(manager, "24"), gates("prettier"));

    assertThat(executor.commands).containsExactly(List.of(executable, "install"));
    assertThat(executor.workingDirectories).containsExactly(root);
    assertThat(change.tools()).containsExactly("prettier");
  }

  @Test
  void addsPinnedVersionsAndKeepsDeclaredOnes() throws IOException {
    configurator.apply(project(BuildTool.NPM, "24"), gates("tsc", "prettier"));

    assertThat(Files.readString(root.resolve("package.json")))
        .contains("\"typescript\": \"^5.9.3\",\n    \"prettier\": \"3.9.10\"\n");
  }

  @Test
  void runsNoInstallWhenNothingIsMissing() throws IOException {
    final BuildFileChange change = configurator.apply(project(BuildTool.NPM, "24"), gates("tsc"));

    assertThat(executor.commands).isEmpty();
    assertThat(change.changed()).isFalse();
    assertThat(Files.readString(root.resolve("package.json"))).isEqualTo(MANIFEST);
  }

  @Test
  void failedInstallRestoresPackageJsonAndTheLockFile() throws IOException {
    Files.writeString(root.resolve("package-lock.json"), LOCK);
    final NodeBuildToolConfigurator failing =
        new NodeBuildToolConfigurator(
            new FileSystemPackageManifests(),
            new FakeCommandExecutor(1, "", "") {
              @Override
              public dev.ward.domain.process.CommandResult execute(
                  final List<String> command, final Path workingDirectory) {
                try {
                  Files.writeString(
                      workingDirectory.resolve("package-lock.json"),
                      "{\"lockfileVersion\": 3, \"changed\": true}\n");
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
                return super.execute(command, workingDirectory);
              }
            });

    assertThatThrownBy(() -> failing.apply(project(BuildTool.NPM, "24"), gates("prettier")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("`npm install` failed");
    assertThat(Files.readString(root.resolve("package.json"))).isEqualTo(MANIFEST);
    assertThat(Files.readString(root.resolve("package-lock.json"))).isEqualTo(LOCK);
  }

  @Test
  void rollbackDeletesALockFileTheInstallCreated() throws IOException {
    final BuildFileChange change =
        configurator.apply(project(BuildTool.PNPM, "24"), gates("prettier"));
    Files.writeString(root.resolve("pnpm-lock.yaml"), "lockfileVersion: '9.0'\n");

    configurator.rollback(change);

    assertThat(root.resolve("pnpm-lock.yaml")).doesNotExist();
    assertThat(Files.readString(root.resolve("package.json"))).isEqualTo(MANIFEST);
  }

  @Test
  void unsupportedAngularMajorIsReportedBeforeInitChangesAnyFile() throws IOException {
    final String unsupportedManifest =
        """
        {
          "name": "shop",
          "devDependencies": {
            "@angular/core": "^19.0.0",
            "typescript": "6.0.3"
          }
        }
        """;
    Files.writeString(root.resolve("package.json"), unsupportedManifest);
    final Project angular =
        new Project(
            root,
            Language.TYPESCRIPT,
            BuildTool.NPM,
            Framework.ANGULAR,
            false,
            false,
            root,
            Set.of(),
            "24",
            TaskRunner.NONE);
    final Throwable thrown =
        catchThrowable(() -> configurator.apply(angular, gates("ng-test", "eslint")));

    assertThat(thrown)
        .isInstanceOf(WardException.class)
        .hasMessageContaining("@angular/core ^19.0.0", "supported Angular majors 21, 22");
    assertThat(Files.readString(root.resolve("package.json"))).isEqualTo(unsupportedManifest);
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void planListsThePackagesAndTheInstall() {
    final PlannedFileChange plan =
        configurator.plan(project(BuildTool.PNPM, "24"), gates("eslint"), null, false);

    assertThat(plan.action()).isEqualTo(PlannedFileChange.Action.MODIFY);
    assertThat(plan.details())
        .containsExactly(
            "add devDependency eslint 10.12.0",
            "add devDependency @eslint/js 10.0.1",
            "add devDependency typescript-eslint 8.71.1",
            "run pnpm install, which updates pnpm-lock.yaml");
    assertThat(executor.commands).isEmpty();
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
        TaskRunner.NONE);
  }

  private static List<InitGateOption> gates(final String... ids) {
    return List.of(ids).stream()
        .map(id -> new InitGateOption(id, id, List.of("npx", id), true, "available"))
        .toList();
  }
}
