package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.GradleToolConfigurator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init on an Android project writes the Android gates and their blocks into the app module. */
class AndroidInitCliTest {
  private static final Path FIXTURE = Path.of("src/test/resources/fixtures/android-project");
  private static final String APP = "app/build.gradle.kts";

  @TempDir Path root;

  @Test
  void initConfiguresTheAndroidGatesAndTheAppModuleBlock() throws IOException {
    copyFixture();
    final InitCommandFixture init =
        InitCommandFixture.repository(root, new GradleToolConfigurator(), "gradle");

    final int exit = init.run("", "--yes", "--integration", "none", "--preset", "standard");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ward.toml")))
        .contains("android-lint", "lintDebug", "testDebugUnitTest", "android-unit-test")
        .contains("createDebugUnitTestCoverageReport", "minimum = 80")
        .doesNotContain("jacocoTestCoverageVerification");
    assertThat(Files.readString(root.resolve(APP)))
        .contains("enableUnitTestCoverage = true")
        .doesNotContain("checkstyle", "pmd", "spotbugs");
  }

  private void copyFixture() throws IOException {
    try (Stream<Path> files = Files.walk(FIXTURE)) {
      for (final Path source : files.toList()) {
        final Path target = root.resolve(FIXTURE.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(target);
        } else {
          Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
      }
    }
  }
}
