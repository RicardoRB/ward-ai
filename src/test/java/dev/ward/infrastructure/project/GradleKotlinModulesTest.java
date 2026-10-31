package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.doctor.SystemEnvironmentInspection;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Kotlin modules are found from their build scripts, skipping buildSrc and nested builds. */
class GradleKotlinModulesTest {
  private static final Path FIXTURE = Path.of("src/test/resources/fixtures/gradle-kotlin-modules");
  private static final String KOTLIN_JVM_KTS =
      "plugins {\n    kotlin(\"jvm\") version \"2.1.0\"\n}\n";

  @TempDir Path root;

  @Test
  void findsModulesInKotlinAndGroovyDslsOnly() {
    assertThat(GradleKotlinModules.of(FIXTURE))
        .extracting(path -> path.getFileName().toString())
        .containsExactlyInAnyOrder("api", "domain");
  }

  @Test
  void countsAliasedKotlinJvmPluginAndSkipsNestedBuildsAndConventionModules() throws IOException {
    write("settings.gradle.kts", "include(\"api\", \"tools\", \"app\")\n");
    write("api/build.gradle.kts", "plugins {\n    alias(libs.plugins.kotlin.jvm)\n}\n");
    write("tools/settings.gradle", "include 'child'\n");
    write("tools/build.gradle", "plugins { id 'org.jetbrains.kotlin.jvm' }\n");
    write("tools/child/build.gradle", "plugins { id 'org.jetbrains.kotlin.jvm' }\n");
    write("app/build.gradle", "plugins { id 'acme.kotlin-conventions' }\n");

    assertThat(GradleKotlinModules.of(root))
        .extracting(path -> path.getFileName().toString())
        .containsExactly("api");
  }

  @Test
  void skipsBuildSrc() throws IOException {
    write("settings.gradle.kts", "include(\"api\")\n");
    write("buildSrc/build.gradle.kts", KOTLIN_JVM_KTS);

    assertThat(GradleKotlinModules.of(root)).isEmpty();
  }

  @Test
  void rootOnlyProjectHasNoModules() throws IOException {
    write("settings.gradle.kts", "rootProject.name = \"solo\"\n");
    write("build.gradle.kts", KOTLIN_JVM_KTS);

    assertThat(GradleKotlinModules.of(root)).isEmpty();
  }

  @Test
  void environmentAdapterReportsTheModulesOfTheFixture() {
    final List<Path> modules =
        new SystemEnvironmentInspection(new ProcessCommandExecutor()).gradleKotlinModules(FIXTURE);

    assertThat(modules).hasSize(2);
  }

  private void write(final String name, final String content) throws IOException {
    final Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
