package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reads the platform of an Xcode project from its {@code SDKROOT} values. */
class XcodebuildPlatformTest {
  private static final String PROJECT_JSON =
      "{\"project\": {\"name\": \"Shop\", \"schemes\": [\"Shop\"]}}";
  private static final String MACOS_PBXPROJ =
      "buildSettings = {\n\tSDKROOT = macosx;\n};\nSDKROOT = macosx;\n";
  private static final String IOS_PBXPROJ =
      "buildSettings = {\n\tSDKROOT = iphoneos;\n};\nSDKROOT = macosx;\n";

  @TempDir Path root;

  @Test
  void macOsOnlyProjectIsDetected() throws IOException {
    makeProjectFile(root, MACOS_PBXPROJ);

    assertThat(schemesRunning(PROJECT_JSON).macOsOnly(root)).isTrue();
  }

  @Test
  void iosProjectIsNotMacOsOnly() throws IOException {
    makeProjectFile(root, IOS_PBXPROJ);

    assertThat(schemesRunning(PROJECT_JSON).macOsOnly(root)).isFalse();
  }

  private static void makeProjectFile(final Path root, final String pbxproj) throws IOException {
    final Path project = root.resolve("Shop.xcodeproj");
    Files.createDirectories(project);
    Files.writeString(project.resolve("project.pbxproj"), pbxproj);
  }

  private static XcodebuildSchemes schemesRunning(final String json) {
    return new XcodebuildSchemes(
        new FakeCommandExecutor(new CommandResult(0, json, "", Duration.ZERO)),
        new ForyJsonCodec());
  }
}
