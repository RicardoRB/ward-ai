package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Target Node version detection from version files and {@code engines.node}. */
class NodeVersionDetectionTest {
  private static final String PACKAGE_JSON = "package.json";

  @TempDir Path dir;

  @Test
  void nvmrcGivesTheMajorVersion() throws IOException {
    write(PACKAGE_JSON, "{\"engines\": {\"node\": \">=18\"}}");
    write(".nvmrc", "v20.11.1\n");

    assertThat(detect().languageVersion()).isEqualTo("20");
  }

  @Test
  void nvmrcAliasFallsThroughToEngines() throws IOException {
    write(PACKAGE_JSON, "{\"engines\": {\"node\": \">=22\"}}");
    write(".nvmrc", "lts/*");

    assertThat(detect().languageVersion()).isEqualTo("22");
  }

  @Test
  void nodeVersionFileIsReadAfterNvmrc() throws IOException {
    write(PACKAGE_JSON, "{}");
    write(".node-version", "23.1.0");

    assertThat(detect().languageVersion()).isEqualTo("23");
  }

  @Test
  void enginesRangeGivesItsLowerBound() throws IOException {
    write(PACKAGE_JSON, "{\"engines\": {\"node\": \"^18.18.0 || >=20\"}}");

    assertThat(detect().languageVersion()).isEqualTo("18");
  }

  @Test
  void enginesWithoutANumberDefaults() throws IOException {
    write(PACKAGE_JSON, "{\"engines\": {\"node\": \"*\"}}");

    assertThat(detect().languageVersion()).isEqualTo("24");
  }

  @Test
  void noVersionDeclaredDefaultsTo24() throws IOException {
    write(PACKAGE_JSON, "{}");

    assertThat(detect().languageVersion()).isEqualTo("24");
  }

  private Project detect() {
    return FileSystemProjectInspection.projectAt(dir).orElseThrow();
  }

  private void write(final String name, final String content) throws IOException {
    Files.writeString(dir.resolve(name), content);
  }
}
