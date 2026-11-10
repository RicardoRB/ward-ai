package dev.ward.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.format.FormatConfigurationException;
import dev.ward.domain.format.FormatTarget;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormatFileSelectionTest {

  private static final int WIDE_WIDTH = 120;

  @TempDir Path project;

  @Test
  void selectsEngineSourcesAndSkipsBuildOutputAndHiddenFolders() throws IOException {
    write("src/App.java", "class App {}");
    write("src/web/index.ts", "const a = 1;");
    write("src/notes.txt", "not code");
    write("target/Gen.java", "class Gen {}");
    write("node_modules/lib/index.js", "x");
    write(".hidden/Secret.java", "class Secret {}");
    write("build/dist/A.kt", "class A");

    final List<FormatTarget> targets =
        select(
            Map.of(
                AnalysisLanguage.JAVA, EditorConfigTestSupport.TYPESCRIPT_OPTIONS,
                AnalysisLanguage.TYPESCRIPT, EditorConfigTestSupport.TYPESCRIPT_OPTIONS,
                AnalysisLanguage.JAVASCRIPT, EditorConfigTestSupport.TYPESCRIPT_OPTIONS,
                AnalysisLanguage.KOTLIN, EditorConfigTestSupport.TYPESCRIPT_OPTIONS));

    assertThat(targets)
        .extracting(FormatTarget::relativePath)
        .containsExactly("src/App.java", "src/web/index.ts");
  }

  @Test
  void filesThatGitIgnoresAreNotSelected() throws IOException {
    write("src/app.ts", "const a = 1;");
    write("coverage/lcov-report/app.ts", "const a = 1;");
    final FakeCommandExecutor git =
        new FakeCommandExecutor(
            new CommandResult(
                0, "coverage/lcov-report/app.ts\u0000", "", FakeCommandExecutor.DURATION, null));

    assertThat(
            select(
                git,
                Map.of(AnalysisLanguage.TYPESCRIPT, EditorConfigTestSupport.TYPESCRIPT_OPTIONS)))
        .extracting(FormatTarget::relativePath)
        .containsExactly("src/app.ts");
  }

  @Test
  void onlyLanguagesWithAFormatterAreSelected() throws IOException {
    write("src/App.java", "class App {}");
    write("web/app.ts", "const a = 1;");
    assertThat(
            select(Map.of(AnalysisLanguage.TYPESCRIPT, EditorConfigTestSupport.TYPESCRIPT_OPTIONS)))
        .extracting(FormatTarget::relativePath)
        .containsExactly("web/app.ts");
  }

  @Test
  void excludedFilesAreNotSelected() throws IOException {
    write(".editorconfig", "root = true\n[src/generated/**]\nward_format = false\n");
    write("src/generated/Api.java", "class Api {}");
    write("src/Main.java", "class Main {}");

    assertThat(select(Map.of(AnalysisLanguage.JAVA, EditorConfigTestSupport.TYPESCRIPT_OPTIONS)))
        .extracting(FormatTarget::relativePath)
        .containsExactly("src/Main.java");
  }

  @Test
  void selectedTargetsCarryTheirResolvedSettings() throws IOException {
    write(".editorconfig", "[*.java]\nmax_line_length = 120\n");
    write("App.java", "class App {}");
    final FormatTarget target =
        select(Map.of(AnalysisLanguage.JAVA, EditorConfigTestSupport.TYPESCRIPT_OPTIONS))
            .getFirst();
    assertThat(target.settings().layout().maxLineLength()).isEqualTo(OptionalInt.of(WIDE_WIDTH));
    assertThat(target.language()).isEqualTo(AnalysisLanguage.JAVA);
  }

  @Test
  void aConfigurationErrorFailsTheWholeSelection() throws IOException {
    write(".editorconfig", "[*.java]\nend_of_line = cr\n");
    write("App.java", "class App {}");
    write("web/app.ts", "const a = 1;");
    assertThatThrownBy(
            () ->
                select(
                    Map.of(
                        AnalysisLanguage.JAVA, EditorConfigTestSupport.TYPESCRIPT_OPTIONS,
                        AnalysisLanguage.TYPESCRIPT, EditorConfigTestSupport.TYPESCRIPT_OPTIONS)))
        .isInstanceOf(FormatConfigurationException.class)
        .hasMessageContaining("end_of_line");
  }

  @Test
  void declaredKeyOfOneLanguageDoesNotApplyToAnother() throws IOException {
    write(".editorconfig", "[*]\nward_continuation_indent_size = 4\n");
    write("App.java", "class App {}");
    write("app.ts", "const a = 1;");
    assertThatThrownBy(
            () ->
                select(
                    Map.of(
                        AnalysisLanguage.JAVA,
                        EditorConfigTestSupport.JAVA_OPTIONS,
                        AnalysisLanguage.TYPESCRIPT,
                        EditorConfigTestSupport.TYPESCRIPT_OPTIONS)))
        .isInstanceOf(FormatConfigurationException.class)
        .hasMessageContaining("ward_continuation_indent_size");
  }

  private List<FormatTarget> select(final Map<AnalysisLanguage, List<FormatOption>> declared) {
    return select(new ProcessCommandExecutor(), declared);
  }

  private List<FormatTarget> select(
      final CommandExecutor executor, final Map<AnalysisLanguage, List<FormatOption>> declared) {
    return new FormatFileSelection(new EditorConfigFormatConfiguration(project), executor)
        .select(project, declared);
  }

  private void write(final String relative, final String text) throws IOException {
    final Path file = project.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text, StandardCharsets.UTF_8);
  }
}
