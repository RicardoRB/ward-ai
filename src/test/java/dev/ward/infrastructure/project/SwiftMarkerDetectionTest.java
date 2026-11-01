package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which files make a directory a Swift project, and which other markers keep precedence. */
class SwiftMarkerDetectionTest {
  private static final String PACKAGE_SWIFT = "Package.swift";
  private static final String APP_XCODEPROJ = "App.xcodeproj";
  private static final String TOOLS_VERSION = "// swift-tools-version: 6.1.2\n";

  @TempDir Path root;

  @Test
  void packageManifestIsSwiftPmProject() throws IOException {
    write(PACKAGE_SWIFT, TOOLS_VERSION);
    write("Sources/App/App.swift", "print(1)\n");

    final Project project = project();

    assertThat(project.language()).isEqualTo(Language.SWIFT);
    assertThat(project.buildTool()).isEqualTo(BuildTool.SWIFTPM);
  }

  @Test
  void xcodeProjectDirectoryIsXcodeProject() throws IOException {
    Files.createDirectories(root.resolve("Shop.xcodeproj"));

    final Project project = project();

    assertThat(project.language()).isEqualTo(Language.SWIFT);
    assertThat(project.buildTool()).isEqualTo(BuildTool.XCODE);
  }

  @Test
  void workspaceDirectoryIsXcodeProject() throws IOException {
    Files.createDirectories(root.resolve("App.xcworkspace"));

    assertThat(project().buildTool()).isEqualTo(BuildTool.XCODE);
  }

  @Test
  void regularFileNamedLikeXcodeProjectIsNoMarker() throws IOException {
    write(APP_XCODEPROJ, "");

    assertThat(FileSystemProjectInspection.projectAt(root)).isEmpty();
  }

  @Test
  void nodeProjectKeepsPrecedenceOverXcodeProject() throws IOException {
    write("package.json", "{\"name\":\"app\"}");
    Files.createDirectories(root.resolve(APP_XCODEPROJ));

    assertThat(project().toolchain()).isEqualTo(Toolchain.NODE);
  }

  @Test
  void mavenProjectKeepsPrecedenceOverPackageManifest() throws IOException {
    write("pom.xml", "<project/>");
    write(PACKAGE_SWIFT, TOOLS_VERSION);

    assertThat(project().buildTool()).isEqualTo(BuildTool.MAVEN);
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
