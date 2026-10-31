package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which Swift build tool wins: Xcode, SwiftPM, Tuist, then XcodeGen. */
class SwiftBuildToolPrecedenceTest {
  private static final String PACKAGE_SWIFT = "Package.swift";
  private static final String APP_XCODEPROJ = "App.xcodeproj";
  private static final String TOOLS_VERSION = "// swift-tools-version: 6.1.2\n";

  @TempDir Path root;

  @Test
  void xcodeWinsOverPackageManifest() throws IOException {
    Files.createDirectories(root.resolve("Shop.xcodeproj"));
    write(PACKAGE_SWIFT, TOOLS_VERSION);

    assertThat(project().buildTool()).isEqualTo(BuildTool.XCODE);
  }

  @Test
  void tuistManifestIsTuistProject() throws IOException {
    write("Project.swift", "import ProjectDescription\n");
    Files.createDirectories(root.resolve("Tuist"));

    final Project project = project();

    assertThat(project.language()).isEqualTo(Language.SWIFT);
    assertThat(project.buildTool()).isEqualTo(BuildTool.TUIST);
    assertThat(project.languageVersion()).isEqualTo("6.2");
  }

  @Test
  void tuistWorkspaceManifestIsTuistProject() throws IOException {
    write("Workspace.swift", "import ProjectDescription\n");

    assertThat(project().buildTool()).isEqualTo(BuildTool.TUIST);
  }

  @Test
  void xcodeGenSpecIsXcodeGenProject() throws IOException {
    write("project.yml", "name: App\n");

    assertThat(project().buildTool()).isEqualTo(BuildTool.XCODEGEN);
  }

  @Test
  void committedXcodeProjectWinsOverGeneratorSpec() throws IOException {
    write("project.yml", "name: App\n");
    Files.createDirectories(root.resolve(APP_XCODEPROJ));

    assertThat(project().buildTool()).isEqualTo(BuildTool.XCODE);
  }

  private Project project() {
    return FileSystemProjectInspection.projectAt(root).orElseThrow();
  }

  private void write(final String relative, final String content) throws IOException {
    final Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
