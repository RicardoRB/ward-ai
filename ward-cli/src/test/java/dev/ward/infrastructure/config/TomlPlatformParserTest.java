package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import dev.ward.domain.config.XcodePlatform;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An xcodebuild-test check names iOS Simulator or macOS, unless its command has a destination. */
class TomlPlatformParserTest {
  private static final String XCODE =
      "version = 2\n[checks.xcode]\ncommand = [\"xcodebuild\", \"test\", \"-scheme\", \"Shop\"]\n";
  private static final String CHECK = "xcode";

  @TempDir Path dir;

  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @Test
  void bothPlatformsAreRead() throws IOException {
    assertThat(
            read(reader, dir, XCODE + "platform = \"iOS Simulator\"")
                .checks()
                .get(CHECK)
                .platform())
        .isEqualTo(XcodePlatform.IOS_SIMULATOR);
    assertThat(read(reader, dir, XCODE + "platform = \"macOS\"").checks().get(CHECK).platform())
        .isEqualTo(XcodePlatform.MACOS);
  }

  @Test
  void unknownPlatformNamesTheCheckAndTheAllowedValues() {
    assertThatThrownBy(() -> read(reader, dir, XCODE + "platform = \"watchOS Simulator\""))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.xcode.platform", "iOS Simulator", "macOS");
  }

  @Test
  void missingPlatformWithoutDestinationIsRejected() {
    assertThatThrownBy(() -> read(reader, dir, XCODE))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.xcode.platform", "iOS Simulator", "macOS");
  }

  @Test
  void explicitDestinationNeedsNoPlatform() throws IOException {
    final String toml =
        "version = 2\n[checks.xcode]\ncommand = [\"xcodebuild\", \"test\", \"-scheme\", \"Shop\","
            + " \"-destination\", \"platform=iOS Simulator,name=iPhone 17 Pro\"]\n";

    assertThat(read(reader, dir, toml).checks().get(CHECK).platform()).isNull();
  }

  @Test
  void platformOnAnotherCheckIsRejected() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    "version = 2\n[checks.build]\ncommand = [\"swift\", \"build\"]\n"
                        + "platform = \"macOS\""))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("checks.build.platform");
  }
}
