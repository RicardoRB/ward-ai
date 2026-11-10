package dev.ward.infrastructure.cli;

import static dev.ward.infrastructure.cli.FormatTestFiles.write;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** The {@code ward-format} gate in {@code ward check}: findings, refusals and partial runs. */
@DisabledOnOs(OS.WINDOWS)
class WardCliFormatGateTest extends WardCliIntegrationSupport {
  private static final String FORMAT = "format";
  private static final String MESSAGE = "file is not formatted";
  private static final String A = "src/A.java";
  private static final String B = "src/B.java";
  private static final String BAD = "src/Bad.py";
  private static final String RAW_JAVA = "class A{int x;}\n";
  private static final String RAW_JAVA_B = "class B{int y;}\n";
  private static final String RAW_PYTHON_ERROR = "def f(:\n    pass\n";
  private static final String GATE_TOML =
      """
      version = 2
      [checks.ward-format]
      uses = "ward-format"
      profiles = ["default", "edit"]
      [checks.ward-format.paths]
      match = ["src/**/*.java"]
      """;

  @Test
  void theGateFailsOnceForEachUnformattedFile() throws IOException {
    write(project, "ward.toml", GATE_TOML);
    write(project, A, RAW_JAVA);

    assertThat(run("check", "-C", project.toString(), "--format", "json"))
        .isEqualTo(ExitCodes.FAILED);

    assertThat(out.toString()).contains("\"ward-format\"").contains(MESSAGE).contains(A);
  }

  @Test
  void theGateOnAFormattedProjectPasses() throws IOException {
    write(project, "ward.toml", GATE_TOML);
    write(project, A, RAW_JAVA);
    run(FORMAT, "-C", project.toString());

    assertThat(run("check", "-C", project.toString())).isEqualTo(ExitCodes.OK);
  }

  @Test
  void theEditProfileChecksOnlyTheEditedFile() throws IOException {
    write(project, "ward.toml", GATE_TOML);
    write(project, A, RAW_JAVA);
    write(project, B, RAW_JAVA_B);

    assertThat(
            run(
                "check",
                "-C",
                project.toString(),
                "--profile",
                "edit",
                "--path",
                A,
                "--format",
                "json"))
        .isEqualTo(ExitCodes.FAILED);

    assertThat(out.toString()).contains(A).doesNotContain(B);
  }

  @Test
  void theGateReportsARefusedFileAsAnExecutionError() throws IOException {
    write(project, "ward.toml", GATE_TOML);
    write(project, BAD, RAW_PYTHON_ERROR);

    assertThat(run("check", "-C", project.toString(), "--format", "json"))
        .isNotEqualTo(ExitCodes.OK);

    assertThat(out.toString()).contains("EXECUTION_ERROR").doesNotContain("\"PASSED\"");
  }
}
