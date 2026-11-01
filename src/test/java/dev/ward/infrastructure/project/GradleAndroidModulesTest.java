package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Android modules are found from the Android Gradle Plugin in their build scripts. */
class GradleAndroidModulesTest {
  private static final Path FIXTURE = Path.of("src/test/resources/fixtures/android-project");

  @TempDir Path root;

  @Test
  void findsApplicationAndLibraryModulesOfTheFixtureButNotTheRoot() {
    assertThat(GradleAndroidModules.of(FIXTURE))
        .extracting(path -> path.getFileName().toString())
        .containsExactlyInAnyOrder("app", "core");
  }

  @Test
  void countsPluginIdsAndCatalogAliasesAndSkipsBuildSrcAndNestedBuilds() throws IOException {
    write("settings.gradle", "include 'app', 'lib', 'tools'\n");
    write("app/build.gradle", "plugins { id 'com.android.application' }\n");
    write("lib/build.gradle.kts", "plugins {\n    alias(libs.plugins.android.library)\n}\n");
    write("tools/settings.gradle", "include 'child'\n");
    write("tools/child/build.gradle", "plugins { id 'com.android.library' }\n");
    write("buildSrc/build.gradle.kts", "plugins {\n    id(\"com.android.library\")\n}\n");
    write("core/build.gradle", "plugins { id 'java-library' }\n");

    assertThat(GradleAndroidModules.of(root))
        .extracting(path -> path.getFileName().toString())
        .containsExactlyInAnyOrder("app", "lib");
  }

  @Test
  void aggregatorWithoutThePluginHasNoAndroidModules() throws IOException {
    write("settings.gradle.kts", "include(\"api\")\n");
    write("build.gradle.kts", "plugins {\n    kotlin(\"jvm\") version \"2.1.0\" apply false\n}\n");
    write("api/build.gradle.kts", "plugins {\n    kotlin(\"jvm\")\n}\n");

    assertThat(GradleAndroidModules.of(root)).isEmpty();
  }

  private void write(final String name, final String text) throws IOException {
    final Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
