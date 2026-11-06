package dev.ward.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.format.FormatConfigurationException;
import dev.ward.domain.format.FormatLayout;
import dev.ward.domain.format.FormatLayout.ByteOrderMark;
import dev.ward.domain.format.FormatLayout.IndentStyle;
import dev.ward.domain.format.FormatLayout.LineEnding;
import dev.ward.domain.format.FormatSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class EditorConfigStandardKeysTest extends EditorConfigTestSupport {

  @Test
  void tabIndentUsesTabWidth() throws IOException {
    write("[*]\nindent_style = tab\nindent_size = tab\ntab_width = 6\n");
    final FormatLayout layout = resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS).layout();
    assertThat(layout.indentStyle()).isEqualTo(IndentStyle.TABS);
    assertThat(layout.indentSize()).isEqualTo(TAB_WIDTH);
    assertThat(layout.tabWidth()).isEqualTo(TAB_WIDTH);
  }

  @Test
  void offMeansNoLineLimit() throws IOException {
    write("[*]\nmax_line_length = off\n");
    assertThat(resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS).layout().maxLineLength())
        .isEqualTo(OptionalInt.empty());
  }

  @Test
  void endOfLineIsUnsetByDefaultAndHonorsLfAndCrlf() throws IOException {
    assertThat(resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS).layout().lineEnding())
        .isEqualTo(Optional.empty());
    write("[*]\nend_of_line = crlf\n");
    assertThat(resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS).layout().lineEnding())
        .isEqualTo(Optional.of(LineEnding.CRLF));
  }

  @Test
  void charsetUtf8BomRequiresTheByteOrderMark() throws IOException {
    write("[*]\ncharset = utf-8-bom\ninsert_final_newline = false\n");
    final FormatLayout layout = resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS).layout();
    assertThat(layout.byteOrderMark()).isEqualTo(ByteOrderMark.REQUIRED);
    assertThat(layout.insertFinalNewline()).isFalse();
  }

  @Test
  void wardOptionsAreReadAndExcludeFiles() throws IOException {
    write("[*]\nward_max_blank_lines = 2\nward_format = false\n");
    final FormatSettings settings = resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS);
    assertThat(settings.maxBlankLines()).isEqualTo(BLANK_LINES);
    assertThat(settings.enabled()).isFalse();
  }

  @Test
  void declaredLanguageKeyIsAcceptedForItsLanguage() throws IOException {
    write("[*.java]\nward_continuation_indent_size = 12\n");
    assertThat(
            resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS)
                .options()
                .get("continuation-indent-size"))
        .isEqualTo(String.valueOf(CONTINUATION_VALUE));
  }

  @Test
  void otherToolsKeysAreIgnored() throws IOException {
    write("[*]\nij_java_align_multiline_parameters = true\nktlint_code_style = ktlint_official\n");
    assertThat(resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS).layout().indentSize())
        .isEqualTo(JAVA_INDENT);
  }

  @Test
  void byteOrderMarkOfTheConfigurationFileIsIgnored() throws IOException {
    write("﻿[*]\nindent_size = 3\n");
    assertThat(resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS).layout().indentSize())
        .isEqualTo(SHORT_INDENT);
  }

  @Test
  void unreadableEditorConfigFailsWithItsPath() throws IOException {
    Files.write(
        project().resolve(".editorconfig"), new byte[] {INVALID_LEAD_BYTE, INVALID_TRAIL_BYTE});
    assertThatThrownBy(() -> resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS))
        .isInstanceOf(FormatConfigurationException.class)
        .hasMessageContaining(".editorconfig");
  }
}
