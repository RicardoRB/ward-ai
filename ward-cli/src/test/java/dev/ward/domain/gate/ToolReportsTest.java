package dev.ward.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ToolReportsTest {
  private static final Path ROOT = Path.of("/repo/app");

  private static String pit(final Language language, final BuildTool buildTool) {
    return ToolReports.pitMutations(new Project(ROOT, language, buildTool, Framework.NONE));
  }

  @Test
  void mavenPitReportLivesInTarget() {
    assertThat(pit(Language.JAVA, BuildTool.MAVEN)).isEqualTo("target/pit-reports/mutations.xml");
  }

  @Test
  void gradlePitReportLivesInBuild() {
    assertThat(pit(Language.KOTLIN, BuildTool.GRADLE))
        .isEqualTo("build/reports/pitest/mutations.xml");
  }
}
