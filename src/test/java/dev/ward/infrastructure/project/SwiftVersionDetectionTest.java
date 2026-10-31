package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The target Swift version of a Swift project, and its CocoaPods flag. */
class SwiftVersionDetectionTest {
  private static final String PACKAGE_SWIFT = "Package.swift";
  private static final String APP_XCODEPROJ = "App.xcodeproj";
  private static final String TOOLS_VERSION = "// swift-tools-version: 6.1.2\n";

  @TempDir Path root;

  @Test
  void toolsVersionIsReadAsMajorMinor() throws IOException {
    write(PACKAGE_SWIFT, TOOLS_VERSION + "import PackageDescription\n");

    assertThat(project().languageVersion()).isEqualTo("6.1");
  }

  @Test
  void highestXcodeBuildSettingIsTheVersion() throws IOException {
    write(
        APP_XCODEPROJ + "/project.pbxproj",
        "\t\t\t\tSWIFT_VERSION = 5.0;\n\t\t\t\tSWIFT_VERSION = 6.0;\n\t\t\t\tSWIFT_VERSION = 5.9;\n");

    assertThat(project().languageVersion()).isEqualTo("6.0");
  }

  @Test
  void xcodeProjectWithoutBuildSettingDefaultsToSwiftOfXcode26() throws IOException {
    Files.createDirectories(root.resolve(APP_XCODEPROJ));

    assertThat(project().languageVersion()).isEqualTo("6.2");
  }

  @Test
  void packageManifestWithoutToolsVersionDefaultsToSwiftOfXcode26() throws IOException {
    write(PACKAGE_SWIFT, "import PackageDescription\n");

    assertThat(project().languageVersion()).isEqualTo("6.2");
  }

  @Test
  void podfileIsReportedAsTheWrapperFlag() throws IOException {
    Files.createDirectories(root.resolve(APP_XCODEPROJ));
    write("Podfile", "platform :ios, '17.0'\n");

    assertThat(project().wrapperAvailable()).isTrue();
  }

  @Test
  void xcodeProjectWithoutPodfileHasNoWrapper() throws IOException {
    Files.createDirectories(root.resolve(APP_XCODEPROJ));

    assertThat(project().wrapperAvailable()).isFalse();
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
