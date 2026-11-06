package dev.ward.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.format.FormatOptionType;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.format.FormatConfigurationException;
import dev.ward.domain.format.FormatLayout.IndentStyle;
import dev.ward.domain.format.FormatSettings;
import java.io.IOException;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** The option values a formatter receives: its declared defaults, overridden by .editorconfig. */
class EditorConfigOptionMappingTest extends EditorConfigTestSupport {

  private static final int CUSTOM_WIDTH = 80;
  private static final int CUSTOM_INDENT = 3;
  private static final int TAB_WIDTH = 6;
  private static final int BLANK_LINES = 2;

  @Test
  void declaredDefaultsAreUsedWhenNoEditorConfigSetsTheOption() throws IOException {
    final FormatOption width =
        new FormatOption("line-width", FormatOptionType.INTEGER, "80", List.of());
    final FormatOption indent =
        new FormatOption("indent-width", FormatOptionType.INTEGER, "3", List.of());
    final FormatSettings settings =
        resolve(
            JAVA_FILE,
            AnalysisLanguage.JAVA,
            List.of(width, INDENT_STYLE_OPTION, MAX_BLANK_LINES_OPTION, indent));
    assertThat(settings.layout().maxLineLength()).isEqualTo(OptionalInt.of(CUSTOM_WIDTH));
    assertThat(settings.layout().indentSize()).isEqualTo(CUSTOM_INDENT);
    assertThat(settings.options().get("line-width")).isEqualTo(String.valueOf(CUSTOM_WIDTH));
    assertThat(settings.options().get("indent-width")).isEqualTo(String.valueOf(CUSTOM_INDENT));
  }

  @Test
  void lineWidthOffBecomesMaxIntegerInOptionsOnly() throws IOException {
    write("[*]\nmax_line_length = off\n");
    final FormatSettings settings = resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS);
    assertThat(settings.options().get("line-width")).isEqualTo(String.valueOf(Integer.MAX_VALUE));
    assertThat(settings.layout().maxLineLength()).isEqualTo(OptionalInt.empty());
  }

  @Test
  void indentStyleAndWidthMapFromEditorConfig() throws IOException {
    write("[*]\nindent_style = tab\nindent_size = 3\n");
    final FormatSettings settings = resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS);
    assertThat(settings.options().get("indent-style")).isEqualTo("tab");
    assertThat(settings.options().get("indent-width")).isEqualTo(String.valueOf(CUSTOM_INDENT));
    assertThat(settings.layout().indentStyle()).isEqualTo(IndentStyle.TABS);
  }

  @Test
  void tabIndentWidthComesFromTabWidth() throws IOException {
    write("[*]\nindent_size = tab\ntab_width = 6\n");
    final FormatSettings settings = resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS);
    assertThat(settings.options().get("indent-width")).isEqualTo(String.valueOf(TAB_WIDTH));
  }

  @Test
  void tabIndentWithoutTabWidthUsesTheDeclaredWidth() throws IOException {
    write("[*]\nindent_size = tab\n");
    final FormatSettings settings = resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS);
    assertThat(settings.options().get("indent-width")).isEqualTo("4");
  }

  @Test
  void wardMaxBlankLinesMapsToTheSharedOption() throws IOException {
    write("[*]\nward_max_blank_lines = 2\n");
    final FormatSettings settings = resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS);
    assertThat(settings.options().get("max-blank-lines")).isEqualTo(String.valueOf(BLANK_LINES));
    assertThat(settings.maxBlankLines()).isEqualTo(BLANK_LINES);
  }

  @Test
  void undeclaredSharedOptionIsNotPassed() throws IOException {
    write("[*]\nmax_line_length = 120\n");
    final FormatSettings settings =
        resolve(JAVA_FILE, AnalysisLanguage.JAVA, List.of(CONTINUATION));
    assertThat(settings.options().values()).containsOnlyKeys("continuation-indent-size");
  }

  @Test
  void sharedKeyIsNotAcceptedUnderTheWardPrefix() throws IOException {
    write("[*]\nward_line_width = 90\n");
    assertThatThrownBy(() -> resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS))
        .isInstanceOf(FormatConfigurationException.class)
        .hasMessageContaining("ward_line_width");
  }
}
