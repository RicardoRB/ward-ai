package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.ProjectFixtures;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** api-compat compares sources with a release tag, so init offers it to Java projects with one. */
class InitApiCompatCatalogTest {
  private static final String API_COMPAT = "api-compat";

  private static Project detect(final Path dir) {
    return new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
  }

  @Test
  void offersMavenApiCompatWithAReleaseTagAndNoCommand(@TempDir final Path dir) throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    final Project project = detect(dir);
    final InitSetupCatalog tagged =
        new InitSetupCatalog(new FakeEnvironmentInspection().withReleaseTag());

    final InitGateOption gate = tagged.gate(project, API_COMPAT);

    assertThat(gate.command()).isEmpty();
    assertThat(gate.available()).isTrue();
    assertThat(tagged.gates(project, null)).extracting(InitGateOption::id).contains(API_COMPAT);
  }

  @Test
  void hidesApiCompatWithoutAReleaseTagAndRefusesItByName(@TempDir final Path dir)
      throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    final Project project = detect(dir);
    final InitSetupCatalog untagged = new InitSetupCatalog(new FakeEnvironmentInspection());

    assertThat(untagged.gates(project, null))
        .extracting(InitGateOption::id)
        .doesNotContain(API_COMPAT);
    assertThatThrownBy(() -> untagged.gate(project, API_COMPAT))
        .hasMessageContaining("release tag")
        .hasMessageContaining("v1.2.3");
  }

  @Test
  void offersGradleApiCompatWithAReleaseTagToo(@TempDir final Path dir) throws Exception {
    Files.writeString(dir.resolve("build.gradle"), "plugins { id 'java' }\n");
    final Project project = detect(dir);

    assertThat(
            new InitSetupCatalog(new FakeEnvironmentInspection("gradle").withReleaseTag())
                .gate(project, API_COMPAT)
                .command())
        .isEmpty();
    assertThat(new InitSetupCatalog(new FakeEnvironmentInspection("gradle")).gates(project, null))
        .extracting(InitGateOption::id)
        .doesNotContain(API_COMPAT);
  }

  @Test
  void kotlinKeepsJapicmpUntilTheNativeCheckReadsKotlin(@TempDir final Path dir) throws Exception {
    Files.writeString(dir.resolve("build.gradle.kts"), "plugins { kotlin(\"jvm\") }\n");
    Files.createDirectories(dir.resolve("src/main/kotlin"));
    Files.writeString(dir.resolve("src/main/kotlin/A.kt"), "class A\n");
    final Project project = detect(dir);

    assertThat(
            new InitSetupCatalog(new FakeEnvironmentInspection("gradle").withReleaseTag())
                .gate(project, API_COMPAT)
                .command())
        .endsWith("japicmp");
  }
}
