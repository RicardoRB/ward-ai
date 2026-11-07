package dev.ward.infrastructure.cli;

import static dev.ward.infrastructure.cli.FormatTestFiles.read;
import static dev.ward.infrastructure.cli.FormatTestFiles.write;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** {@code ward format} over each engine language, and the settings that change its output. */
@DisabledOnOs(OS.WINDOWS)
class WardCliFormatLanguagesTest extends WardCliIntegrationSupport {
  private static final String FORMAT = "format";
  private static final String CHECK_FLAG = "--check";
  private static final String A = "src/A.java";
  private static final String B = "src/B.java";
  private static final String RAW_JAVA = "class A{int x;}\n";
  private static final String RAW_JAVA_B = "class B{int y;}\n";

  @Test
  void everyEngineLanguageIsFormattedAndThenStable() throws IOException {
    write(project, "app/Main.kt", "package app\n\nclass Main {\nval x:Int=1\n}\n");
    write(project, "web/app.ts", "const a:number=1\n");
    write(project, "web/app.js", "const b=2\n");
    write(project, "py/main.py", "def f(x):\n      return x\n");
    write(project, "ios/App.swift", "struct App{let x:Int}\n");

    final int status = run(FORMAT, "-C", project.toString());
    assertThat(err.toString()).isEmpty();
    assertThat(status).isEqualTo(ExitCodes.OK);
    assertThat(read(project, "app/Main.kt")).contains("class Main");
    assertThat(read(project, "ios/App.swift")).isNotEqualTo("struct App{let x:Int}\n");
    assertThat(run(FORMAT, CHECK_FLAG, "-C", project.toString())).isEqualTo(ExitCodes.OK);
  }

  @Test
  void typeScriptKeepsItsQuotesAndDropsTheTrailingCommaOfACollapsedList() throws IOException {
    write(project, "web/app.ts", "const a = \"x\"\nconst b = [1, 2,]\n");

    assertThat(run(FORMAT, "-C", project.toString())).isEqualTo(ExitCodes.OK);

    assertThat(read(project, "web/app.ts"))
        .contains("\"x\"")
        .contains("[1, 2]")
        .doesNotContain(";");
  }

  @Test
  void javaCommentsStayOnTheirOwnLinesOrTheirStatement() throws IOException {
    write(project, A, "class A {\n// before\nint x = 1; // after\n}\n");

    assertThat(run(FORMAT, "-C", project.toString())).isEqualTo(ExitCodes.OK);

    final String text = read(project, A);
    assertThat(text.lines().map(String::strip)).contains("// before");
    assertThat(text.lines().filter(line -> line.contains("int x")))
        .singleElement()
        .satisfies(line -> assertThat(line).contains("// after"));
  }

  @Test
  void anUnsetEndOfLineKeepsCrlf() throws IOException {
    write(project, A, RAW_JAVA.replace("\n", "\r\n"));

    assertThat(run(FORMAT, "-C", project.toString())).isEqualTo(ExitCodes.OK);

    assertThat(read(project, A).replace("\r\n", "")).doesNotContain("\n");
  }

  @Test
  void wardFormatFalseExcludesAFile() throws IOException {
    write(project, B, RAW_JAVA_B);
    write(project, A, RAW_JAVA);
    write(project, ".editorconfig", "root = true\n\n[src/B.java]\nward_format = false\n");

    assertThat(run(FORMAT, "-C", project.toString())).isEqualTo(ExitCodes.OK);

    assertThat(read(project, A)).isNotEqualTo(RAW_JAVA);
    assertThat(read(project, B)).isEqualTo(RAW_JAVA_B);
  }

  @Test
  void anInvalidEditorConfigValueFormatsNothingAndExitsTwo() throws IOException {
    write(project, A, RAW_JAVA);
    write(project, ".editorconfig", "root = true\n\n[*.java]\nend_of_line = cr\n");

    assertThat(run(FORMAT, "-C", project.toString())).isEqualTo(ExitCodes.ERROR);

    assertThat(err.toString()).contains(".editorconfig").contains("end_of_line");
    assertThat(read(project, A)).isEqualTo(RAW_JAVA);
  }
}
