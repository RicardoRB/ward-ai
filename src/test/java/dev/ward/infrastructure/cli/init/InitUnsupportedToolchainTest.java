package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectSnapshot;
import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init refuses a project of a toolchain it cannot configure instead of treating it as Maven. */
class InitUnsupportedToolchainTest {
  @TempDir Path root;

  @Test
  void projectOfAnUnsupportedToolchainFailsWithoutChangingFiles() throws IOException {
    final InitCommandFixture init = InitCommandFixture.repository(root);
    Files.writeString(root.resolve("pyproject.toml"), "[project]\nname = \"demo\"\n");
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit = init.run("", "--integration", "none", "--gate", "tests", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output())
        .contains("Poetry")
        .contains("Ward init supports Maven, Gradle, TypeScript and JavaScript projects");
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  @Test
  void javaScriptPackageWithoutSourcesIsNoSupportedProject() throws IOException {
    final InitCommandFixture init = InitCommandFixture.repository(root);
    Files.writeString(root.resolve("package.json"), "{\"name\": \"demo\"}\n");
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit = init.run("", "--integration", "none", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output()).contains("No supported project detected.");
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  @Test
  void strictPresetIsRejectedForTypeScript() throws IOException {
    final InitCommandFixture init = InitCommandFixture.repository(root);
    Files.writeString(root.resolve("package.json"), "{\"name\": \"demo\"}\n");
    Files.writeString(root.resolve("tsconfig.json"), "{}\n");
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit = init.run("", "--integration", "none", "--preset", "strict", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output())
        .contains("strict preset is not available for TypeScript yet")
        .contains("--preset standard");
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  @Test
  void strictPresetIsRejectedForJavaScript() throws IOException {
    final InitCommandFixture init = InitCommandFixture.repository(root);
    Files.writeString(root.resolve("package.json"), "{\"name\": \"demo\"}\n");
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/index.js"), "module.exports = 1;\n");
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit = init.run("", "--integration", "none", "--preset", "strict", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output())
        .contains("strict preset is not available for JavaScript yet")
        .contains("--preset standard");
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }
}
