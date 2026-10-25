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
import org.junit.jupiter.params.provider.CsvSource;

class MavenCompatibilityInspectionTest {
  private static final int JAVA_11 = 11;
  private static final int JAVA_17 = 17;
  private static final String POM = "pom.xml";
  private static final String NO_RELEASE = "<project/>";

  @Test
  void readsToolchainPropertiesAndDeclaredPluginVersions(@TempDir final Path root)
      throws Exception {
    Files.writeString(
        root.resolve(POM),
        """
        <project>
          <properties><java.version>17</java.version></properties>
          <build><plugins><plugin>
            <artifactId>spotless-maven-plugin</artifactId><version>2.44.4</version>
          </plugin></plugins></build>
        </project>
        """);
    final FakeCommandExecutor commands =
        new FakeCommandExecutor(0, "Apache Maven 3.9.8\nJava version: 17.0.15, vendor: x\n", "");

    final CompatibilityProjectSnapshot snapshot =
        new MavenCompatibilityInspection(commands).inspect(project(root));

    assertThat(snapshot.toolchain().declaredRelease()).contains(JAVA_17);
    assertThat(snapshot.toolchain().runtimeJdk()).contains(JAVA_17);
    assertThat(snapshot.toolchain().buildToolVersion()).contains("3.9.8");
    assertThat(snapshot.pluginVersion("spotless-maven-plugin")).contains("2.44.4");
    assertThat(commands.commands).containsExactly(List.of("mvn", "--version"));
  }

  @ParameterizedTest
  @CsvSource({"1.8.0_412, 8", "17.0.15, 17", "25, 25", "25.0.5, 25"})
  void readsTheRuntimeJdkFromJavaVersion(
      final String reported, final int feature, @TempDir final Path root) throws Exception {
    Files.writeString(root.resolve(POM), NO_RELEASE);

    final CompatibilityProjectSnapshot snapshot =
        new MavenCompatibilityInspection(
                new FakeCommandExecutor(
                    0,
                    "Apache Maven 3.9.11\nMaven home: /opt/maven\nJava version: "
                        + reported
                        + ", vendor: Eclipse Adoptium, runtime: /opt/jdk\n",
                    ""))
            .inspect(project(root));

    assertThat(snapshot.toolchain().runtimeJdk()).contains(feature);
  }

  @Test
  void noReleaseDeclaredResolvesForTheRuntimeJdkNotWardsJvm(@TempDir final Path root)
      throws Exception {
    Files.writeString(root.resolve(POM), NO_RELEASE);

    final CompatibilityProjectSnapshot snapshot =
        new MavenCompatibilityInspection(
                new FakeCommandExecutor(0, "Apache Maven 3.9.8\nJava version: 11.0.32\n", ""))
            .inspect(project(root));

    assertThat(snapshot.toolchain().declaredRelease()).isEmpty();
    assertThat(snapshot.toolchain().release()).contains(JAVA_11);
  }

  @Test
  void missingVersionOutputLeavesTheRuntimeJdkUnknown(@TempDir final Path root) throws Exception {
    Files.writeString(root.resolve(POM), NO_RELEASE);

    final CompatibilityProjectSnapshot snapshot =
        new MavenCompatibilityInspection(new FakeCommandExecutor(1, "", "mvn: not found"))
            .inspect(project(root));

    assertThat(snapshot.toolchain().runtimeJdk()).isEmpty();
    assertThat(snapshot.toolchain().buildToolVersion()).isEmpty();
    assertThat(snapshot.toolchain().release()).isEmpty();
  }

  @Test
  void treatsUnresolvedCompilerPropertyAsUnknown(@TempDir final Path root) throws Exception {
    Files.writeString(
        root.resolve(POM),
        "<project><properties><maven.compiler.release>${missing.release}</maven.compiler.release>"
            + "</properties></project>");

    final CompatibilityProjectSnapshot snapshot =
        new MavenCompatibilityInspection(new FakeCommandExecutor(0, "Apache Maven 3.9.8\n", ""))
            .inspect(project(root));

    assertThat(snapshot.toolchain().declaredRelease()).isEmpty();
  }

  private static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE, false);
  }
}
