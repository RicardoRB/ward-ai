package dev.ward.infrastructure.init.maven;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenCodeRootTest {
  @Test
  void aggregatorWithoutSourcesResolvesToItsModuleWithSources(@TempDir final Path root)
      throws IOException {
    write(
        root.resolve("pom.xml"), "<project><modules><module>ward-cli</module></modules></project>");
    write(root.resolve("ward-cli/pom.xml"), "<project/>");
    Files.createDirectories(root.resolve("ward-cli/src/main/java"));

    assertThat(MavenCodeRoot.of(maven(root)).root()).isEqualTo(root.resolve("ward-cli"));
  }

  @Test
  void projectWithOwnSourcesIsUnchanged(@TempDir final Path root) throws IOException {
    write(
        root.resolve("pom.xml"), "<project><modules><module>ward-cli</module></modules></project>");
    Files.createDirectories(root.resolve("src/main/java"));
    Files.createDirectories(root.resolve("ward-cli/src/main/java"));

    assertThat(MavenCodeRoot.of(maven(root))).isEqualTo(maven(root));
  }

  @Test
  void aggregatorWithoutModuleSourcesIsUnchanged(@TempDir final Path root) throws IOException {
    write(
        root.resolve("pom.xml"), "<project><modules><module>ward-cli</module></modules></project>");
    write(root.resolve("ward-cli/pom.xml"), "<project/>");

    assertThat(MavenCodeRoot.of(maven(root))).isEqualTo(maven(root));
  }

  @Test
  void gradleProjectIsUnchanged(@TempDir final Path root) {
    final Project gradle = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);

    assertThat(MavenCodeRoot.of(gradle)).isEqualTo(gradle);
  }

  private static Project maven(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static void write(final Path file, final String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
