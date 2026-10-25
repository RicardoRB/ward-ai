package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.init.CompatibilityProjectSnapshot;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GradleCompatibilityInspectionTest {
  private static final int JAVA_8 = 8;
  private static final int JAVA_17 = 17;
  private static final String GROOVY = "build.gradle";
  private static final String VERSION_OUTPUT =
      """

      ------------------------------------------------------------
      Gradle 8.14.6
      ------------------------------------------------------------

      Build time:    2026-09-01 10:00:00 UTC
      Kotlin:        2.0.21
      Groovy:        3.0.24
      Launcher JVM:  1.8.0_482 (Amazon.com Inc. 25.482-b08)
      Daemon JVM:    /opt/jdk8 (no JDK specified, using current Java home)
      OS:            Linux 6.8.0 amd64
      """;

  @Test
  void readsTheGradleVersionLauncherJvmAndPlugins(@TempDir final Path root) throws Exception {
    Files.writeString(
        root.resolve(GROOVY),
        "plugins {\n    id 'java'\n    id 'com.github.spotbugs' version '6.0.26'\n}\n");
    final FakeCommandExecutor commands = new FakeCommandExecutor(0, VERSION_OUTPUT, "");

    final CompatibilityProjectSnapshot snapshot =
        new GradleCompatibilityInspection(commands).inspect(project(root));

    assertThat(snapshot.toolchain().buildToolVersion()).contains("8.14.6");
    assertThat(snapshot.toolchain().runtimeJdk()).contains(JAVA_8);
    assertThat(snapshot.toolchain().declaredRelease()).isEmpty();
    assertThat(snapshot.pluginVersion("com.github.spotbugs")).contains("6.0.26");
    assertThat(commands.commands).containsExactly(List.of("gradle", "--version"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }",
        "java {\n    toolchain {\n        languageVersion.set(JavaLanguageVersion.of(17))\n    }\n}",
        "sourceCompatibility = JavaVersion.VERSION_17",
        "java { sourceCompatibility = JavaVersion.toVersion('17') }",
        "sourceCompatibility = '17'",
        "sourceCompatibility = 17",
        "tasks.withType(JavaCompile) { options.release = 17 }",
        "tasks.withType<JavaCompile> { options.release.set(17) }"
      })
  void readsTheReleaseFromTheBuildScript(final String script) {
    assertThat(GradleCompatibilityInspection.release(script)).contains(JAVA_17);
  }

  @Test
  void readsALegacySourceCompatibility() {
    assertThat(
            GradleCompatibilityInspection.release("sourceCompatibility = JavaVersion.VERSION_1_8"))
        .contains(JAVA_8);
    assertThat(GradleCompatibilityInspection.release("sourceCompatibility = '1.8'"))
        .contains(JAVA_8);
  }

  @Test
  void computedReleaseFallsBackToTheLauncherJvm(@TempDir final Path root) throws Exception {
    Files.writeString(
        root.resolve("build.gradle.kts"),
        "java { toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get()) } }");

    final CompatibilityProjectSnapshot snapshot =
        new GradleCompatibilityInspection(new FakeCommandExecutor(0, VERSION_OUTPUT, ""))
            .inspect(project(root));

    assertThat(snapshot.toolchain().declaredRelease()).isEmpty();
    assertThat(snapshot.toolchain().release()).contains(JAVA_8);
  }

  @Test
  void gradleReleaseFromTheBuildFile(@TempDir final Path root) throws Exception {
    Files.writeString(
        root.resolve(GROOVY),
        "java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }\n");

    final CompatibilityProjectSnapshot snapshot =
        new GradleCompatibilityInspection(
                new FakeCommandExecutor(
                    0, "Gradle 9.8.2\nLauncher JVM:  17.0.15 (Eclipse Adoptium 17.0.15+6)\n", ""))
            .inspect(project(root));

    assertThat(snapshot.toolchain().runtimeJdk()).contains(JAVA_17);
    assertThat(snapshot.toolchain().release()).contains(JAVA_17);
  }

  @Test
  void missingGradleLeavesTheToolchainUnknown(@TempDir final Path root) throws Exception {
    Files.writeString(root.resolve(GROOVY), "plugins { id 'java' }\n");

    final CompatibilityProjectSnapshot snapshot =
        new GradleCompatibilityInspection(new FakeCommandExecutor(1, "", "gradle: not found"))
            .inspect(project(root));

    assertThat(snapshot.toolchain().runtimeJdk()).isEmpty();
    assertThat(snapshot.toolchain().buildToolVersion()).isEmpty();
  }

  private static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE, false);
  }
}
