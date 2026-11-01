package dev.ward.infrastructure.init.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ModuleFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Java module with sources gets the module-placed gates in its own script, in both DSLs. */
class GradleJavaModulePlacementTest {
  private static final String JAVA_KTS = "plugins {\n    `java-library`\n}\n";
  private static final String JAVA_GROOVY = "plugins {\n    id 'java-library'\n}\n";

  @TempDir Path root;

  @Test
  void placesCoverageInEachJavaModuleScriptAndRollsBackEveryScript() throws IOException {
    Files.writeString(root.resolve("settings.gradle.kts"), "include(\"api\", \"domain\")\n");
    Files.writeString(root.resolve("build.gradle.kts"), "plugins {\n}\n");
    write("api/build.gradle.kts", JAVA_KTS);
    write("domain/build.gradle", JAVA_GROOVY);
    Files.createDirectories(root.resolve("api/src/main/java"));
    Files.createDirectories(root.resolve("domain/src/test/java"));
    final Project project = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
    final String api = Files.readString(root.resolve("api/build.gradle.kts"));
    final String domain = Files.readString(root.resolve("domain/build.gradle"));

    final GradleToolConfigurator configurator = new GradleToolConfigurator();
    final BuildFileChange change =
        configurator.apply(project, ModuleFixtures.gates("coverage"), null, finding -> false);

    assertThat(Files.readString(root.resolve("api/build.gradle.kts")))
        .contains("jacocoTestCoverageVerification");
    assertThat(Files.readString(root.resolve("domain/build.gradle")))
        .contains("jacocoTestCoverageVerification {");
    assertThat(Files.readString(root.resolve("build.gradle.kts")))
        .doesNotContain("jacocoTestCoverageVerification");

    configurator.rollback(change);

    assertThat(Files.readString(root.resolve("api/build.gradle.kts"))).isEqualTo(api);
    assertThat(Files.readString(root.resolve("domain/build.gradle"))).isEqualTo(domain);
  }

  @Test
  void versionedModulePluginIsDeclaredInTheRootWithApplyFalseAndUnversionedInModules()
      throws IOException {
    Files.writeString(root.resolve("settings.gradle.kts"), "include(\"api\")\n");
    Files.writeString(root.resolve("build.gradle.kts"), "plugins {\n}\n");
    write("api/build.gradle.kts", JAVA_KTS);
    Files.createDirectories(root.resolve("api/src/main/java"));
    final Project project = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);

    new GradleToolConfigurator()
        .apply(project, ModuleFixtures.gates("spotbugs"), null, finding -> false);

    assertThat(Files.readString(root.resolve("build.gradle.kts")))
        .containsPattern("id\\(\"com\\.github\\.spotbugs\"\\) version \"[^\"]+\" apply false");
    assertThat(Files.readString(root.resolve("api/build.gradle.kts")))
        .contains("id(\"com.github.spotbugs\")")
        .doesNotContain("com.github.spotbugs\") version");
  }

  private void write(final String name, final String content) throws IOException {
    final Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
