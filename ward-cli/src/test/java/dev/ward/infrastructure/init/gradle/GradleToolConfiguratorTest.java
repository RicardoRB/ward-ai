package dev.ward.infrastructure.init.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleToolConfiguratorTest {
  @Test
  void configuresGroovyBuildAndRollsBack(@TempDir final Path root) throws Exception {
    final Path build = root.resolve("build.gradle");
    Files.writeString(build, "plugins { id 'java' }\n\n// user configuration\n");
    final Project project = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
    final GradleToolConfigurator configurator = new GradleToolConfigurator();

    final BuildFileChange change =
        configurator.apply(project, List.of(gate("checkstyle")), null, finding -> false);

    final String updated = Files.readString(build);
    assertThat(updated).contains("checkstyle", "user configuration");
    assertThat(change.file()).isEqualTo(build);
    configurator.rollback(change);
    assertThat(Files.readString(build)).isEqualTo(change.originalContent());
  }

  @Test
  void usesPinnedGradlePluginDeclarationsForAllManagedGates(@TempDir final Path root)
      throws Exception {
    final Path build = root.resolve("build.gradle");
    Files.writeString(build, "plugins { id 'java' }\n");
    final Project project = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
    final List<String> ids =
        List.of(
            "spotbugs",
            "coverage",
            "dependency-check",
            "mutation",
            "sonar",
            "license",
            "api-compat");

    new GradleToolConfigurator()
        .apply(
            project,
            ids.stream().map(GradleToolConfiguratorTest::gate).toList(),
            null,
            finding -> false);

    assertThat(Files.readString(build))
        .contains(
            "com.github.spotbugs",
            "org.owasp.dependencycheck",
            "info.solidsoft.pitest",
            "org.sonarqube",
            "com.github.jk1.dependency-license-report",
            "me.champeau.gradle.japicmp");
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("./gradlew", id), true, "available");
  }
}
