package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The problems of the whole file: syntax, keys and version. */
class ArchitectureFileShapeTest {
  @TempDir Path root;

  @Test
  void textThatIsNotTomlNamesTheFileAndThePosition() throws IOException {
    assertThat(ArchitectureFiles.problemsOf(root, "version = = 1\n"))
        .singleElement()
        .asString()
        .startsWith("ward-architecture.toml: not valid TOML at ");
  }

  @Test
  void aTopLevelCommandIsAnUnknownKey() throws IOException {
    assertThat(ArchitectureFiles.problemsOf(root, "version = 1\ncommand = [\"rm\"]\n"))
        .singleElement()
        .asString()
        .contains("ward-architecture.toml", "command", "unknown key");
  }

  @Test
  void anUnsupportedVersionIsAProblem() throws IOException {
    assertThat(ArchitectureFiles.problemsOf(root, "version = 2\n"))
        .singleElement()
        .asString()
        .contains("version");
    assertThat(ArchitectureFiles.problemsOf(root, ""))
        .singleElement()
        .asString()
        .contains("version");
  }
}
