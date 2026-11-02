package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** {@code detect} and the JSON report show the target Python version of Python projects only. */
@DisabledOnOs(OS.WINDOWS)
class WardCliPythonProjectTest extends WardCliIntegrationSupport {
  private static final String LANGUAGE_VERSION = "languageVersion";
  private static final String PROJECT = "project";

  @TempDir Path python;

  @Test
  void detectPrintsThePythonVersionOfAPythonProject() throws IOException {
    pythonProject();

    assertThat(run("detect", "-C", python.toString())).isZero();
    assertThat(out.toString()).contains("Python", "pip", "Python:       3.12");
  }

  @Test
  void detectPrintsNoPythonLineForJava() {
    assertThat(run("detect", "-C", project.toString())).isZero();
    assertThat(out.toString()).doesNotContain("Python:");
  }

  @Test
  void jsonReportHasTheLanguageVersionOfAPythonProject() throws IOException {
    pythonProject();
    Files.writeString(
        python.resolve(WARD_TOML),
        """
        version = 2
        [checks.format]
        command = ["true"]
        """);

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", python.toString())).isZero();
    assertThat(JsonTree.parse(out.toString()).get(PROJECT).get(LANGUAGE_VERSION).asString())
        .isEqualTo("3.12");
  }

  @Test
  void jsonReportOfAMavenProjectHasNoLanguageVersion() {
    assertThat(run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, "format", "-C", project.toString()))
        .isZero();
    out.getBuffer().setLength(0);

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString())).isZero();
    assertThat(JsonTree.parse(out.toString()).get(PROJECT).has(LANGUAGE_VERSION)).isFalse();
  }

  private void pythonProject() throws IOException {
    Files.writeString(python.resolve("pyproject.toml"), "[project]\nname = \"app\"\n");
    Files.writeString(python.resolve(".python-version"), "3.12.4\n");
  }
}
