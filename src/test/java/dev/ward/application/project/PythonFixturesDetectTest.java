package dev.ward.application.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Each offline Python init fixture is detected as a Python project of the expected manager. */
class PythonFixturesDetectTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

  @Test
  void uvPoetryPipAndRequirementsFixturesDetectTheirManager() {
    final ProjectDetector detector = new ProjectDetector(new FileSystemProjectInspection());

    assertThat(detector.detect(FIXTURES.resolve("python-uv")))
        .hasValueSatisfying(project -> assertThat(project.buildTool()).isEqualTo(BuildTool.UV));
    assertThat(detector.detect(FIXTURES.resolve("python-poetry")))
        .hasValueSatisfying(project -> assertThat(project.buildTool()).isEqualTo(BuildTool.POETRY));
    assertThat(detector.detect(FIXTURES.resolve("python-pip")))
        .hasValueSatisfying(
            project -> {
              assertThat(project.buildTool()).isEqualTo(BuildTool.PIP);
              assertThat(project.language()).isEqualTo(Language.PYTHON);
              assertThat(project.framework()).isEqualTo(Framework.NONE);
            });
    assertThat(detector.detect(FIXTURES.resolve("python-requirements")))
        .hasValueSatisfying(project -> assertThat(project.buildTool()).isEqualTo(BuildTool.PIP));
  }
}
