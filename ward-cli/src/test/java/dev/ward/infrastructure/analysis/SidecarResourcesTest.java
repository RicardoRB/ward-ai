package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The type sidecar scripts ship in the native image only when their glob is registered. */
class SidecarResourcesTest {
  private static final Path METADATA =
      Path.of(
          "src/main/resources/META-INF/native-image/dev.ward/ward-cli/reachability-metadata.json");

  @Test
  void sidecarScriptsAreRegisteredForTheNativeImage() throws IOException {
    assertThat(Files.readString(METADATA)).contains("\"glob\": \"dev/ward/sidecar/*\"");
  }

  @Test
  void bundledScriptsExistOnTheClasspath() {
    assertThat(SidecarScripts.class.getResource("/dev/ward/sidecar/WardJavaTypes.java"))
        .isNotNull();
    assertThat(SidecarScripts.class.getResource("/dev/ward/sidecar/ward-ts-types.mjs")).isNotNull();
  }
}
