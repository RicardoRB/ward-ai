package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Gradle project that applies the Android Gradle Plugin is reported as Android. */
class FileSystemAndroidProjectTest {
  private static final Path FIXTURE = Path.of("src/test/resources/fixtures/android-project");

  @TempDir Path root;

  @Test
  void androidFixtureIsReportedAsAndroidWithGradle() {
    final Optional<Project> project = FileSystemProjectInspection.projectAt(FIXTURE);

    assertThat(project).isPresent();
    assertThat(project.get().framework()).isEqualTo(Framework.ANDROID);
  }

  @Test
  void moduleOnlyAndroidAppIsReportedAsAndroid() throws IOException {
    write("settings.gradle.kts", "include(\"app\")\n");
    write("build.gradle.kts", "plugins {\n    kotlin(\"jvm\") version \"2.1.0\" apply false\n}\n");
    write("app/build.gradle.kts", "plugins {\n    id(\"com.android.application\")\n}\n");

    assertThat(FileSystemProjectInspection.projectAt(root).orElseThrow().framework())
        .isEqualTo(Framework.ANDROID);
  }

  @Test
  void androidPluginWinsOverAServerDependencyGroup() throws IOException {
    write(
        "build.gradle.kts",
        "plugins {\n    id(\"com.android.application\")\n}\n"
            + "dependencies { implementation(\"io.ktor:ktor-client-core:3.0.0\") }\n");

    assertThat(FileSystemProjectInspection.projectAt(root).orElseThrow().framework())
        .isEqualTo(Framework.ANDROID);
  }

  private void write(final String name, final String text) throws IOException {
    final Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
