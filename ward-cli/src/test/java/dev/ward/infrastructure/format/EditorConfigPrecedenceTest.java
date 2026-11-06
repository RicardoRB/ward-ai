package dev.ward.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.format.FormatConfiguration;
import dev.ward.domain.format.FormatLayout;
import dev.ward.domain.format.FormatLayout.IndentStyle;
import dev.ward.domain.format.FormatSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class EditorConfigPrecedenceTest extends EditorConfigTestSupport {

  @Test
  void noEditorConfigUsesBuiltInDefaults() throws IOException {
    final FormatSettings java = resolve("App.java", AnalysisLanguage.JAVA, JAVA_OPTIONS);
    assertThat(java.enabled()).isTrue();
    assertThat(java.layout().maxLineLength()).isEqualTo(OptionalInt.of(DEFAULT_LINE_WIDTH));
    assertThat(java.layout().indentStyle()).isEqualTo(IndentStyle.SPACES);
    assertThat(java.layout().indentSize()).isEqualTo(JAVA_INDENT);
    assertThat(java.layout().tabWidth()).isEqualTo(JAVA_INDENT);
    assertThat(java.layout().insertFinalNewline()).isTrue();
    assertThat(java.maxBlankLines()).isEqualTo(DEFAULT_MAX_BLANK_LINES);
    assertThat(java.options().get("continuation-indent-size")).isEqualTo("8");

    final FormatSettings typescript =
        resolve("app.ts", AnalysisLanguage.TYPESCRIPT, TYPESCRIPT_OPTIONS);
    assertThat(typescript.layout().maxLineLength()).isEqualTo(OptionalInt.of(DEFAULT_LINE_WIDTH));
    assertThat(typescript.layout().indentSize()).isEqualTo(TYPESCRIPT_INDENT);
  }

  @Test
  void laterSectionOverridesEarlierAndGlobSectionsApplyPerFile() throws IOException {
    write(".editorconfig", "[*]\nmax_line_length = 120\n[*.py]\nmax_line_length = 88\n");
    assertThat(
            resolve("app.py", AnalysisLanguage.PYTHON, TYPESCRIPT_OPTIONS).layout().maxLineLength())
        .isEqualTo(OptionalInt.of(PYTHON_WIDTH));
    assertThat(resolve("App.java", AnalysisLanguage.JAVA, JAVA_OPTIONS).layout().maxLineLength())
        .isEqualTo(OptionalInt.of(WIDE_WIDTH));
  }

  @Test
  void laterMatchingSectionOverridesEarlierSameFile() throws IOException {
    write("[*]\nindent_size = 3\n[*.java]\nindent_size = 6\n");
    assertThat(resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS).layout().indentSize())
        .isEqualTo(MEDIUM_INDENT);
  }

  @Test
  void nestedFileOverridesRootFile() throws IOException {
    write("[*.ts]\nindent_size = 4\n");
    write("web/.editorconfig", "[*.ts]\nindent_size = 2\n");
    assertThat(
            resolve("web/app.ts", AnalysisLanguage.TYPESCRIPT, TYPESCRIPT_OPTIONS)
                .layout()
                .indentSize())
        .isEqualTo(TYPESCRIPT_INDENT);
    assertThat(
            resolve("api/app.ts", AnalysisLanguage.TYPESCRIPT, TYPESCRIPT_OPTIONS)
                .layout()
                .indentSize())
        .isEqualTo(JAVA_INDENT);
  }

  @Test
  void rootTrueStopsTheLookupAboveIt() throws IOException {
    write("[*]\nindent_size = 8\n");
    write("module/.editorconfig", "root = true\n[*]\nmax_line_length = 60\n");
    final FormatLayout module =
        resolve("module/A.java", AnalysisLanguage.JAVA, JAVA_OPTIONS).layout();
    assertThat(module.maxLineLength()).isEqualTo(OptionalInt.of(NARROW_WIDTH));
    assertThat(module.indentSize()).isEqualTo(JAVA_INDENT);
    assertThat(resolve("other/A.java", AnalysisLanguage.JAVA, JAVA_OPTIONS).layout().indentSize())
        .isEqualTo(PARENT_INDENT);
  }

  @Test
  void lookupNeverPassesTheProjectRoot() throws IOException {
    final Path parent = project().resolve("outer");
    Files.createDirectories(parent.resolve("project"));
    Files.writeString(parent.resolve(".editorconfig"), "[*]\nindent_size = 8\n");
    final FormatConfiguration configuration =
        new EditorConfigFormatConfiguration(parent.resolve("project"));
    assertThat(
            configuration
                .resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS)
                .layout()
                .indentSize())
        .isEqualTo(JAVA_INDENT);
  }

  @Test
  void unsetRemovesAnEarlierValue() throws IOException {
    write("[*]\nmax_line_length = 60\n[*.java]\nmax_line_length = unset\n");
    assertThat(resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS).layout().maxLineLength())
        .isEqualTo(OptionalInt.of(DEFAULT_LINE_WIDTH));
  }
}
