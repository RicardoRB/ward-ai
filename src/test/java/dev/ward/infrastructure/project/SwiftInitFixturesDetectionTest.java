package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Detection reads the offline Swift fixtures that the init tests configure. */
class SwiftInitFixturesDetectionTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

  @Test
  void swiftPackageFixtureIsSwiftPm() {
    assertThat(buildToolOf("swift-package")).isEqualTo(BuildTool.SWIFTPM);
  }

  @Test
  void xcodeFixturesAreXcode() {
    assertThat(buildToolOf("swift-xcode-app")).isEqualTo(BuildTool.XCODE);
    assertThat(buildToolOf("swift-xcode-two-schemes")).isEqualTo(BuildTool.XCODE);
    assertThat(buildToolOf("swift-cocoapods")).isEqualTo(BuildTool.XCODE);
  }

  private BuildTool buildToolOf(final String fixture) {
    return FileSystemProjectInspection.projectAt(FIXTURES.resolve(fixture).toAbsolutePath())
        .orElseThrow()
        .buildTool();
  }
}
