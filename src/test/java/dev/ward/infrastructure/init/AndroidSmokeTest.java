package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the Android gates' AGP tasks on the Android fixture with a real Gradle and Android SDK. It
 * is opt-in, and needs an SDK in {@code ANDROID_HOME} or {@code ANDROID_SDK_ROOT} and network
 * access for the first build: {@code ./mvnw -Dtest=AndroidSmokeTest -Dward.android.smoke=true
 * -Dward.gradle.binary=/path/to/gradle test}.
 */
@EnabledIfSystemProperty(named = "ward.android.smoke", matches = "true")
class AndroidSmokeTest {
  private static final Path FIXTURE = Path.of("src/test/resources/fixtures/android-project");
  private static final long TIMEOUT_MINUTES = 15;
  private static final String REPORT = "core/build/reports/coverage/test/debug/report.xml";

  @TempDir Path root;

  @Test
  void androidGatesRunAndWriteTheCoverageReport() throws IOException, InterruptedException {
    assumeTrue(
        System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null,
        "The Android SDK must be set in ANDROID_HOME or ANDROID_SDK_ROOT.");
    copyFixture();
    new GradleToolConfigurator()
        .apply(
            new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.ANDROID),
            List.of(
                gate("android-lint", List.of("lintDebug")),
                gate("android-unit-test", List.of("testDebugUnitTest")),
                gate("android-coverage", List.of("createDebugUnitTestCoverageReport"))),
            QualityPreset.STANDARD,
            finding -> false);

    final Path log = root.resolve("gradle.log");
    final Process process =
        new ProcessBuilder(
                System.getProperty("ward.gradle.binary", "gradle"),
                "--no-daemon",
                "-q",
                "lintDebug",
                "testDebugUnitTest",
                "createDebugUnitTestCoverageReport")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    assertThat(process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)).isTrue();

    assertThat(process.exitValue()).as(Files.readString(log)).isZero();
    assertThat(root.resolve(REPORT)).exists();
  }

  private static InitGateOption gate(final String id, final List<String> tasks) {
    return new InitGateOption(id, id, tasks, true, "available");
  }

  private void copyFixture() throws IOException {
    try (Stream<Path> files = Files.walk(FIXTURE)) {
      for (final Path source : files.toList()) {
        final Path target = root.resolve(FIXTURE.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(target);
        } else {
          Files.copy(source, target, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        }
      }
    }
  }
}
