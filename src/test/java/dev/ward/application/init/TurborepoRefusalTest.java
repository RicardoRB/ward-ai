package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitSelection;
import dev.ward.infrastructure.init.ArchitectureTestGenerator;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.PomToolConfigurator;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init refuses a Turborepo root before it writes anything, for both plan and apply. */
class TurborepoRefusalTest {
  private static final String PACKAGE_JSON =
      "{\n  \"name\": \"monorepo\",\n  \"workspaces\": [\"apps/*\"]\n}\n";
  private static final InitSelection SELECTION =
      new InitSelection(List.of("none"), List.of("format"), null, null);

  private final InitService service =
      new InitService(
          new ProjectDetector(new FileSystemProjectInspection()),
          Set.of(),
          new InitSetupCatalog(new FakeEnvironmentInspection()),
          new InitProjectFiles(
              new PomToolConfigurator(),
              new PresetRuleFileWriter(),
              new ArchitectureTestGenerator(),
              new FileConfigurationStorage()));

  @Test
  void applyRefusesTurborepoAndWritesNothing(@TempDir final Path dir) throws IOException {
    turborepo(dir);

    assertThatThrownBy(() -> service.initialize(dir, SELECTION))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("Turborepo");
    assertThat(dir.resolve("ward.toml")).doesNotExist();
    assertThat(Files.readString(dir.resolve("package.json"))).isEqualTo(PACKAGE_JSON);
  }

  @Test
  void dryRunPlanRefusesTurborepoToo(@TempDir final Path dir) throws IOException {
    turborepo(dir);

    assertThatThrownBy(() -> service.plan(dir, SELECTION, false))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("Turborepo");
    assertThat(dir.resolve("ward.toml")).doesNotExist();
  }

  private static void turborepo(final Path dir) throws IOException {
    Files.writeString(dir.resolve("package.json"), PACKAGE_JSON);
    Files.writeString(dir.resolve("turbo.json"), "{}\n");
    Files.createDirectories(dir.resolve("apps/web/src"));
    Files.writeString(dir.resolve("apps/web/package.json"), "{\"name\": \"web\"}\n");
    Files.writeString(dir.resolve("apps/web/src/index.ts"), "export {};\n");
  }
}
