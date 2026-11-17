package dev.ward.infrastructure.cli;

import static dev.ward.infrastructure.cli.FormatTestFiles.read;
import static dev.ward.infrastructure.cli.FormatTestFiles.write;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** {@code ward format} run from a subdirectory of the project (issue #637). */
@DisabledOnOs(OS.WINDOWS)
class WardCliFormatSubdirectoryTest extends WardCliIntegrationSupport {
  private static final String FORMAT = "format";
  private static final String RAW = "class A{void f(){int x;}}\n";
  private static final String TOP = "src/main/java/Top.java";
  private static final String NESTED = "src/main/java/deep/Nested.java";
  private static final String EXCLUDED = "src/test/resources/Fixture.java";
  private static final String NESTED_DIRECTORY = "src/main/java/deep";

  @BeforeEach
  void layout() throws IOException {
    write(
        project,
        ".editorconfig",
        "root = true\n\n[*.java]\nindent_style = space\nindent_size = 2\n\n"
            + "[src/test/resources/**]\nward_format = false\n"
    );
    write(project, TOP, RAW);
    write(project, NESTED, RAW);
    write(project, EXCLUDED, RAW);
  }

  @Test
  @DisplayName("ward format from a subdirectory uses the root .editorconfig and its exclusions")
  void formatFromASubdirectoryFormatsTheSameFilesAsFromTheRoot() throws IOException {
    // Given a project whose root has ward.toml and an .editorconfig with indent 2 and an exclusion
    write(project, "ward.toml", "");

    // When ward format runs from a nested directory of the project
    final int exit = run(FORMAT, "-C", project.resolve(NESTED_DIRECTORY).toString());

    // Then every selected file of the project is formatted with indent 2, and the excluded
    // file is untouched
    assertThat(exit).isEqualTo(ExitCodes.OK);
    assertThat(read(project, NESTED)).contains("\n  void f() {");
    assertThat(read(project, TOP)).contains("\n  void f() {");
    assertThat(read(project, EXCLUDED)).isEqualTo(RAW);
  }

  @Test
  @DisplayName(
      "ward format --check from a subdirectory with a git root names project-relative files"
  )
  void checkFromASubdirectoryFindsTheGitRoot() throws IOException {
    // Given a project whose root is a git repository, with no ward.toml
    Files.createDirectories(project.resolve(".git"));

    // When ward format --check runs from a nested directory
    final int exit = run(FORMAT, "--check", "-C", project.resolve(NESTED_DIRECTORY).toString());

    // Then the files are named relative to the root and the excluded file is not reported
    assertThat(exit).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString())
        .contains("needs formatting " + TOP)
        .contains("needs formatting " + NESTED)
        .doesNotContain("Fixture.java");
  }

  @Test
  @DisplayName("a relative path from a subdirectory resolves against that subdirectory")
  void aRelativePathResolvesAgainstTheWorkingDirectory() throws IOException {
    // Given a project with a ward.toml root
    write(project, "ward.toml", "");

    // When ward format names a file relative to the nested directory
    final int exit =
        run(FORMAT, "-C", project.resolve(NESTED_DIRECTORY).toString(), "Nested.java");

    // Then only that file is formatted
    assertThat(exit).isEqualTo(ExitCodes.OK);
    assertThat(read(project, NESTED)).contains("\n  void f() {");
    assertThat(read(project, TOP)).isEqualTo(RAW);
  }
}
