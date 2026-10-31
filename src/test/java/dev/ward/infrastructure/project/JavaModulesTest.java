package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Java modules of Gradle and Maven builds: which are configured and which are skipped. */
class JavaModulesTest {
  private static final String JAVA_KTS = "plugins {\n    `java-library`\n}\n";

  @TempDir Path root;

  @Test
  void gradleModuleWithJavaPluginAndSourcesIsConfiguredAndOneWithoutSourcesIsSkipped()
      throws IOException {
    write("lib/build.gradle.kts", JAVA_KTS);
    Files.createDirectories(root.resolve("lib/src/main/java"));
    write("res/build.gradle.kts", JAVA_KTS);
    write("bare/build.gradle.kts", "plugins {\n}\n");

    assertThat(GradleKotlinModules.of(root)).containsExactly(root.resolve("lib"));
    assertThat(GradleModuleScan.skipped(root)).containsExactlyInAnyOrder("res", "bare");
  }

  @Test
  void mavenModuleWithoutJavaSourcesIsSkipped() throws IOException {
    write("pom.xml", "<project><modules><module>api</module><module>config</module></modules>");
    write("api/pom.xml", "<project/>");
    Files.createDirectories(root.resolve("api/src/test/java"));
    write("config/pom.xml", "<project/>");

    assertThat(MavenJavaModules.of(root)).containsExactly(root.resolve("api/pom.xml"));
    assertThat(MavenJavaModules.skipped(root)).containsExactly("config");
  }

  @Test
  void mavenProjectWithoutModulesHasNone() throws IOException {
    write("pom.xml", "<project/>");

    assertThat(MavenJavaModules.of(root)).isEmpty();
    assertThat(MavenJavaModules.skipped(root)).isEmpty();
  }

  private void write(final String name, final String content) throws IOException {
    final Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
