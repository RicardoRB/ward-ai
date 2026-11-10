package dev.ward.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectFixtures;
import dev.ward.application.project.ProjectDiscovery;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// This test intentionally aggregates coverage across the application services.
// Splitting it would duplicate setup and obscure the cross-service scenarios.
class CoverageProjectDiscoveryTest {
  @Test
  void discoversEverySupportedProjectMarker(@TempDir final Path root) throws IOException {
    Files.createDirectories(root.resolve("maven"));
    Files.writeString(root.resolve("maven/pom.xml"), ProjectFixtures.PLAIN_POM);
    Files.createDirectories(root.resolve("gradle"));
    Files.writeString(root.resolve("gradle/build.gradle"), "");
    Files.createDirectories(root.resolve("kotlin"));
    Files.writeString(root.resolve("kotlin/build.gradle.kts"), "");
    Files.createDirectories(root.resolve("kotlin/src/main/kotlin"));
    Files.createDirectories(root.resolve("typescript"));
    Files.writeString(root.resolve("typescript/tsconfig.json"), "{}");
    Files.createDirectories(root.resolve("javascript"));
    Files.writeString(root.resolve("javascript/package.json"), "{}");
    Files.writeString(root.resolve("javascript/yarn.lock"), "");
    Files.createDirectories(root.resolve("python-poetry"));
    Files.writeString(
        root.resolve("python-poetry/pyproject.toml"), "[tool.poetry]\nname = \"demo\"\n");
    Files.createDirectories(root.resolve("python-pip"));
    Files.writeString(root.resolve("python-pip/requirements.txt"), "");
    Files.createDirectories(root.resolve("go"));
    Files.writeString(root.resolve("go/go.mod"), "module example");
    Files.createDirectories(root.resolve("rust"));
    Files.writeString(root.resolve("rust/Cargo.toml"), "[package]");
    Files.createDirectories(root.resolve("csharp"));
    Files.writeString(root.resolve("csharp/app.csproj"), "<Project/>");

    assertThat(new ProjectDiscovery(new FileSystemProjectInspection()).discover(root))
        .extracting(discovered -> discovered.project().language())
        .containsExactlyInAnyOrder(
            Language.JAVA,
            Language.JAVA,
            Language.KOTLIN,
            Language.TYPESCRIPT,
            Language.JAVASCRIPT,
            Language.PYTHON,
            Language.PYTHON,
            Language.GO,
            Language.RUST,
            Language.CSHARP);
    assertThat(
            new ProjectDiscovery(new FileSystemProjectInspection()).discover(root.resolve("maven")))
        .hasSize(1);
  }

  @Test
  void projectDiscoverySelectsPackageManagerMarkers(@TempDir final Path root) throws IOException {
    Files.writeString(root.resolve("package.json"), "{}");
    Files.writeString(root.resolve("pnpm-lock.yaml"), "");
    assertThat(
            new ProjectDiscovery(new FileSystemProjectInspection())
                .discover(root)
                .getFirst()
                .project()
                .buildTool())
        .isEqualTo(BuildTool.PNPM);
    Files.delete(root.resolve("pnpm-lock.yaml"));
    Files.writeString(root.resolve("yarn.lock"), "");
    assertThat(
            new ProjectDiscovery(new FileSystemProjectInspection())
                .discover(root)
                .getFirst()
                .project()
                .buildTool())
        .isEqualTo(BuildTool.YARN);
  }
}
