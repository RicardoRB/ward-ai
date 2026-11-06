package dev.ward.infrastructure.cli;

import static dev.ward.infrastructure.cli.FormatTestFiles.read;
import static dev.ward.infrastructure.cli.FormatTestFiles.write;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** {@code ward format}: the command, its exit codes and its JSON report. */
@DisabledOnOs(OS.WINDOWS)
class WardCliFormatTest extends WardCliIntegrationSupport {
  private static final String FORMAT = "format";
  private static final String CHECK_FLAG = "--check";
  private static final String RAW_JAVA = "class A{int x;}\n";
  private static final String RAW_JAVA_B = "class B{int y;}\n";
  private static final String RAW_PYTHON_ERROR = "def f(:\n    pass\n";
  private static final String A = "src/A.java";
  private static final String B = "src/B.java";
  private static final String BAD = "src/Bad.py";

  @BeforeEach
  void sources() throws IOException {
    write(project, A, RAW_JAVA);
    write(project, B, RAW_JAVA_B);
  }

  @Test
  void checkModeNamesTheFileAndWritesNothing() throws IOException {
    assertThat(run(FORMAT, CHECK_FLAG, "-C", project.toString())).isEqualTo(ExitCodes.FAILED);

    assertThat(out.toString()).contains("needs formatting " + A).contains(B);
    assertThat(read(project, A)).isEqualTo(RAW_JAVA);
  }

  @Test
  void formatWritesTheFilesAndAFormattedProjectPassesCheck() throws IOException {
    assertThat(run(FORMAT, "-C", project.toString())).isEqualTo(ExitCodes.OK);
    assertThat(read(project, A)).isNotEqualTo(RAW_JAVA).contains("class A {");

    out.getBuffer().setLength(0);
    assertThat(run(FORMAT, CHECK_FLAG, "-C", project.toString())).isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).doesNotContain("needs formatting");
  }

  @Test
  void pathsLimitTheRunToThoseFiles() throws IOException {
    assertThat(run(FORMAT, "-C", project.toString(), A)).isEqualTo(ExitCodes.OK);

    assertThat(read(project, A)).isNotEqualTo(RAW_JAVA);
    assertThat(read(project, B)).isEqualTo(RAW_JAVA_B);
  }

  @Test
  void aFolderPathFormatsEveryFileBelowIt() throws IOException {
    assertThat(run(FORMAT, "-C", project.toString(), "src")).isEqualTo(ExitCodes.OK);

    assertThat(read(project, A)).isNotEqualTo(RAW_JAVA);
    assertThat(read(project, B)).isNotEqualTo(RAW_JAVA_B);
  }

  @Test
  void aPathWithNoFormattableFileIsAnError() {
    assertThat(run(FORMAT, "-C", project.toString(), "README.md")).isEqualTo(ExitCodes.ERROR);

    assertThat(err.toString()).contains("No formattable file at README.md");
  }

  @Test
  void aRefusedFileIsLeftUntouchedAndExitsTwo() throws IOException {
    write(project, BAD, RAW_PYTHON_ERROR);

    assertThat(run(FORMAT, "-C", project.toString(), BAD)).isEqualTo(ExitCodes.ERROR);

    assertThat(err.toString()).contains("refused " + BAD + ":1");
    assertThat(read(project, BAD)).isEqualTo(RAW_PYTHON_ERROR);
  }

  @Test
  void jsonOutputIsOnlyJsonOnStdout() {
    assertThat(run(FORMAT, CHECK_FLAG, "-C", project.toString(), "--format", "json"))
        .isEqualTo(ExitCodes.FAILED);

    final JsonTree report = JsonTree.parse(out.toString());
    assertThat(report.get("files").get(0).get("status").asString()).isEqualTo("needs-formatting");
    assertThat(report.get("files").get(0).get("path").asString()).isEqualTo(A);
    assertThat(report.get("files").get(0).get("line").asInt()).isEqualTo(1);
  }
}
