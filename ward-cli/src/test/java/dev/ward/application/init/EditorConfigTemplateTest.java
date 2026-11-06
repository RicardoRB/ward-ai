package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Language;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The {@code .editorconfig} body that the ward format engine writes for the detected languages. */
class EditorConfigTemplateTest {

  @Test
  void javaAndKotlinGetFourSpacesAndTheContinuationIndent() {
    final String body = EditorConfigTemplate.body(Set.of(Language.JAVA, Language.KOTLIN));

    assertThat(body)
        .startsWith("root = true\n\n[*]\nmax_line_length = 100\n")
        .contains("ward_max_blank_lines = 1\n")
        .contains("\n[*.{java,kt,kts}]\nindent_size = 4\n")
        .contains("\n[*.{java,kt,kts}]\nward_continuation_indent_size = 8\n")
        .doesNotContain("*.ts");
  }

  @Test
  void typeScriptGetsTwoSpacesAndNoContinuationIndent() {
    final String body = EditorConfigTemplate.body(Set.of(Language.TYPESCRIPT));

    assertThat(body)
        .contains("\n[*.{ts,tsx,js,jsx,mjs,cjs}]\nindent_size = 2\n")
        .doesNotContain("ward_continuation_indent_size", "[*.{java");
  }

  @Test
  void aSingleExtensionIsWrittenWithoutBraces() {
    assertThat(EditorConfigTemplate.body(Set.of(Language.PYTHON)))
        .contains("\n[*.py]\nindent_size = 4\n")
        .doesNotContain("{");
  }

  @Test
  void sourceGlobsNameTheFilesOfEachLanguage() {
    assertThat(EditorConfigTemplate.sourceGlobs(Set.of(Language.SWIFT)))
        .containsExactly("**/*.swift");
  }
}
