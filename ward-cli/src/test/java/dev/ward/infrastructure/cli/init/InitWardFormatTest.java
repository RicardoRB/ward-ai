package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.init.SwiftPorts;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.swift.SwiftToolchainInspection;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.JvmBuildToolConfigurator;
import dev.ward.infrastructure.init.gradle.GradleToolConfigurator;
import dev.ward.infrastructure.init.maven.PomToolConfigurator;
import dev.ward.infrastructure.init.swift.SwiftBuildToolConfigurator;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.project.swift.FileSystemSwiftSourceDirectories;
import dev.ward.infrastructure.project.swift.FileSystemSwiftToolConfigs;
import dev.ward.infrastructure.project.swift.XcodebuildSchemes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init writes the {@code ward-format} gate and, without one, the {@code .editorconfig} it reads. */
class InitWardFormatTest {

  @TempDir Path root;

  @Test
  void mavenInitWritesWardFormatAndTheEditorConfigWithoutSpotless() throws IOException {
    mavenProject();

    final InitCommandFixture init = init();
    final int exit =
        init.run(
            "",
            "--preset",
            "standard",
            "--integration",
            "none",
            "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ward.toml")))
        .contains("[checks.ward-format]", "uses = \"ward-format\"")
        .doesNotContain("[checks.spotless]");
    assertThat(Files.readString(root.resolve("pom.xml"))).doesNotContain("spotless-maven-plugin");
    assertThat(Files.readString(root.resolve(".editorconfig")))
        .startsWith("# ward-owned: editorconfig v1 begin\nroot = true\n")
        .contains("max_line_length = 100", "[*.java]\nindent_size = 4\n")
        .contains("[*.java]\nindent_size = 4\n", "ward_continuation_indent_size = 8");
  }

  @Test
  void gradleInitWritesNoSpotlessPluginOrGate() throws IOException {
    Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }\n");
    Files.createDirectories(root.resolve("src/main/java/shop"));
    Files.writeString(root.resolve("src/main/java/shop/App.java"), "package shop;\nclass App {}\n");

    final InitCommandFixture init = init();
    final int exit =
        init.run(
            "",
            "--preset",
            "standard",
            "--integration",
            "none",
            "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ward.toml")))
        .contains("[checks.ward-format]")
        .doesNotContain("[checks.spotless]");
    assertThat(Files.readString(root.resolve("build.gradle.kts")))
        .doesNotContain("com.diffplug.spotless", "spotless {");
    assertThat(Files.readString(root.resolve(".editorconfig"))).contains("[*.java]");
  }

  @Test
  void anExistingEditorConfigIsLeftByteForByteUnchanged() throws IOException {
    mavenProject();
    final String existing = "root = false\n\n[*]\nindent_size = 8\n";
    Files.writeString(root.resolve(".editorconfig"), existing);

    final InitCommandFixture preview = init();
    preview.run(
        "",
        "--preset",
        "standard",
        "--integration",
        "none",
        "--dry-run",
        "--yes");
    assertThat(preview.output()).contains("Ward formats with this .editorconfig");
    assertThat(Files.readString(root.resolve(".editorconfig"))).isEqualTo(existing);

    final InitCommandFixture init = init();
    final int exit =
        init.run(
            "",
            "--preset",
            "standard",
            "--integration",
            "none",
            "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve(".editorconfig"))).isEqualTo(existing);
    assertThat(Files.readString(root.resolve("ward.toml"))).contains("[checks.ward-format]");
  }

  @Test
  void swiftInitWritesNoSwiftFormatConfig() throws IOException {
    copyFixture("swift-package", root);
    final FakeCommandExecutor resolve =
        new FakeCommandExecutor(new CommandResult(0, "", "", Duration.ZERO));
    final InitCommandFixture init =
        InitCommandFixture.repository(root, new SwiftBuildToolConfigurator(resolve, mac()), "swift")
            .withSwiftPorts(
                new SwiftPorts(
                    new XcodebuildSchemes(new FakeCommandExecutor(), new ForyJsonCodec()),
                    new FileSystemSwiftToolConfigs(),
                    new FileSystemSwiftSourceDirectories()));

    final int exit = init.run("", "--preset", "standard", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ward.toml")))
        .contains("[checks.ward-format]")
        .doesNotContain("[checks.swift-format]");
    assertThat(root.resolve(".swift-format")).doesNotExist();
    assertThat(Files.readString(root.resolve(".editorconfig"))).contains("[*.swift]");
  }

  private void mavenProject() throws IOException {
    Files.writeString(
        root.resolve("pom.xml"),
        "<project><modelVersion>4.0.0</modelVersion><groupId>g</groupId>"
            + "<artifactId>a</artifactId><version>1</version></project>\n");
    Files.createDirectories(root.resolve("src/main/java/shop"));
    Files.writeString(root.resolve("src/main/java/shop/App.java"), "package shop;\nclass App {}\n");
  }

  private InitCommandFixture init() throws IOException {
    return InitCommandFixture.repository(
        root,
        new JvmBuildToolConfigurator(new PomToolConfigurator(), new GradleToolConfigurator()));
  }

  private static SwiftToolchainInspection mac() {
    return new SwiftToolchainInspection() {
      @Override
      public Optional<String> xcodeVersion() {
        return Optional.of("26.6");
      }

      @Override
      public boolean macOs() {
        return true;
      }
    };
  }

  private static void copyFixture(final String name, final Path destination) throws IOException {
    final Path source = Path.of("src/test/resources/fixtures").resolve(name);
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.toList()) {
        final Path target = destination.resolve(source.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(file, target);
        }
      }
    }
  }
}
