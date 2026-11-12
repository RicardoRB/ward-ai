package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectFixtures;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The preset's minimums reach ward.toml as the {@code coverage} check's keys, and nowhere else. */
class JvmCoverageMinimumTest {
  @TempDir Path dir;

  private String toml(final Project project, final QualityPreset preset) {
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());
    final List<InitGateOption> gates =
        catalog.gates(project, preset).stream()
            .filter(gate -> "coverage".equals(gate.id()))
            .toList();
    return ConfigurationTemplate.render(gates, preset, project);
  }

  private Project maven() throws IOException {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    return new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
  }

  private Project gradle() throws IOException {
    Files.writeString(dir.resolve("build.gradle"), "plugins { id 'java' }\n");
    return new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
  }

  @Test
  void standardMavenWritesSeventyPercentOfLines() throws IOException {
    assertThat(toml(maven(), QualityPreset.STANDARD))
        .contains("command = \"mvn jacoco:report\"", "minimum = 70")
        .doesNotContain("minimum-branches");
  }

  @Test
  void strictMavenWritesLineAndBranchMinimums() throws IOException {
    assertThat(toml(maven(), QualityPreset.STRICT))
        .contains("minimum = 85", "minimum-branches = 75");
  }

  @Test
  void aCustomSelectionWritesEightyPercentOfLines() throws IOException {
    assertThat(toml(maven(), null)).contains("minimum = 80").doesNotContain("minimum-branches");
  }

  @Test
  void gradleWritesTheSameMinimumsAndRunsTheReportTask() throws IOException {
    final Project project = gradle();

    assertThat(toml(project, QualityPreset.STRICT))
        .contains("jacocoTestReport", "minimum = 85", "minimum-branches = 75");
    assertThat(toml(project, QualityPreset.STANDARD)).contains("minimum = 70");
  }
}
