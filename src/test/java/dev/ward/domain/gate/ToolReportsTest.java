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

  private static String directory(final Language language, final BuildTool buildTool) {
    return ToolReports.buildDirectory(new Project(ROOT, language, buildTool, Framework.NONE));
  }

  @Test
  void mavenReportsLiveInTarget() {
    assertThat(directory(Language.JAVA, BuildTool.MAVEN)).isEqualTo("target");
  }

  @Test
  void gradleReportsLiveInBuild() {
    assertThat(directory(Language.KOTLIN, BuildTool.GRADLE)).isEqualTo("build");
  }

  @Test
  void nodeReportsLiveInWardReportsForEveryManager() {
    assertThat(directory(Language.TYPESCRIPT, BuildTool.NPM)).isEqualTo(".ward/reports");
    assertThat(directory(Language.JAVASCRIPT, BuildTool.BUN)).isEqualTo(".ward/reports");
  }

  @Test
  void pythonReportsLiveInWardReports() {
    assertThat(directory(Language.PYTHON, BuildTool.POETRY)).isEqualTo(".ward/reports");
    assertThat(directory(Language.PYTHON, BuildTool.PIP)).isEqualTo(".ward/reports");
  }

  @Test
  void swiftReportsLiveInWardReports() {
    assertThat(directory(Language.SWIFT, BuildTool.XCODE)).isEqualTo(".ward/reports");
    assertThat(directory(Language.SWIFT, BuildTool.SWIFTPM)).isEqualTo(".ward/reports");
  }
}
