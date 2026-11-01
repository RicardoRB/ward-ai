package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Finds a Swift tool's own config, and ignores a {@code .swiftlint.yml} that Ward wrote. */
class FileSystemSwiftToolConfigsTest {
  @TempDir Path root;

  @Test
  void noConfigMeansNoUserConfig() {
    assertThat(new FileSystemSwiftToolConfigs().userConfig(root, ".swiftlint.yml")).isEmpty();
  }

  @Test
  void userSwiftLintConfigCounts() throws IOException {
    Files.writeString(root.resolve(".swiftlint.yml"), "disabled_rules:\n  - line_length\n");

    assertThat(new FileSystemSwiftToolConfigs().userConfig(root, ".swiftlint.yml"))
        .contains(root.resolve(".swiftlint.yml"));
  }

  @Test
  void wardMarkedSwiftLintConfigDoesNotCount() throws IOException {
    Files.writeString(
        root.resolve(".swiftlint.yml"), "# managed-by: ward\nexcluded:\n  - .build\n");

    assertThat(new FileSystemSwiftToolConfigs().userConfig(root, ".swiftlint.yml")).isEmpty();
  }

  @Test
  void swiftFormatCountsEvenWhenItLooksWardWritten() throws IOException {
    Files.writeString(root.resolve(".swift-format"), "{\"version\": 1}\n");

    assertThat(new FileSystemSwiftToolConfigs().userConfig(root, ".swift-format"))
        .contains(root.resolve(".swift-format"));
  }
}
