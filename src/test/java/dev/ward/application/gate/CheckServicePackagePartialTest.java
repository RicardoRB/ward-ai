package dev.ward.application.gate;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.project.WorkspaceMember;
import dev.ward.domain.project.WorkspaceMembers;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A {@code {package}} partial item names the workspace package that holds each edited file. */
class CheckServicePackagePartialTest {
  private static final String CONFIGURATION =
      """
      version = 2
      [checks.lint]
      uses = "eslint"
      profiles = ["default", "edit"]
      command = "turbo run lint"
      [checks.lint.paths]
      match = ["**/*.ts"]
      args = ["{paths}"]
      item = "--filter=...{package}"
      """;
  private static final String UI_FILE = "packages/ui/src/button.ts";
  private static final String UI_PACKAGE = "@repo/ui";
  private static final List<String> FULL_LINT = List.of("turbo", "run", "lint");

  @TempDir Path dir;

  private final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

  @BeforeEach
  void project() throws IOException {
    withPom(dir, PLAIN_POM);
    Files.writeString(dir.resolve("ward.toml"), CONFIGURATION);
    Files.createDirectories(dir.resolve("tools"));
    Files.createDirectories(dir.resolve("packages/ui/src"));
    Files.writeString(dir.resolve(UI_FILE), "export {};\n");
    Files.writeString(dir.resolve("tools/build.ts"), "export {};\n");
  }

  @Test
  void editInAPackageNarrowsTheGateToThatPackage() {
    checkPartial(UI_FILE);

    assertThat(executor.commands)
        .containsExactly(List.of("turbo", "run", "lint", "--filter=...@repo/ui"));
  }

  @Test
  void editInNoPackageRunsTheGateInFull() {
    checkPartial("tools/build.ts");

    assertThat(executor.commands).containsExactly(FULL_LINT);
  }

  private void checkPartial(final String path) {
    final CheckService checks =
        new CheckService(
            new ProjectDetector(new FileSystemProjectInspection()),
            new TomlConfigurationReader(),
            new QualityGateFactory(executor),
            new QualityGateRunner(),
            new PartialPorts(Files::exists, new PackageMembers(dir)));
    checks.checkPartial(
        dir,
        List.of(dir.resolve(path)),
        new CheckRunOptions(List.of(), CheckProgressListener.NO_OP, false, Set.of()));
  }

  /** Names {@code packages/ui} as {@value #UI_PACKAGE} and lists no other members. */
  private record PackageMembers(Path root) implements WorkspaceMembers {
    @Override
    public List<WorkspaceMember> members(final Path ignored) {
      return List.of();
    }

    @Override
    public Optional<String> packageOf(final Path ignored, final Path path) {
      return path.startsWith(root.resolve("packages/ui"))
          ? Optional.of(UI_PACKAGE)
          : Optional.empty();
    }
  }
}
