package dev.ward.infrastructure.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.format.FormatOptionType;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.format.FormatConfigurationException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class EditorConfigValidationTest extends EditorConfigTestSupport {

  @Test
  void declaredKeyOfAnotherLanguageIsRejected() throws IOException {
    write("[*]\nward_continuation_indent_size = 12\n");
    assertThat(
            resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS)
                .options()
                .get("continuation-indent-size"))
        .isEqualTo(String.valueOf(CONTINUATION_VALUE));
    assertThatThrownBy(() -> resolve("app.ts", AnalysisLanguage.TYPESCRIPT, TYPESCRIPT_OPTIONS))
        .isInstanceOf(FormatConfigurationException.class)
        .hasMessageContaining(".editorconfig")
        .hasMessageContaining("section [*]")
        .hasMessageContaining("ward_continuation_indent_size");
  }

  @Test
  void unsupportedEndOfLineNamesFileSectionKeyAndSupportedValues() throws IOException {
    write("[*.java]\nend_of_line = cr\n");
    assertThatThrownBy(() -> resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS))
        .isInstanceOf(FormatConfigurationException.class)
        .hasMessageContaining(".editorconfig")
        .hasMessageContaining("section [*.java]")
        .hasMessageContaining("end_of_line")
        .hasMessageContaining("lf, crlf");
  }

  @Test
  void unknownWardKeyIsRejected() throws IOException {
    write("[*]\nward_quote_style = single\n");
    assertThatThrownBy(() -> resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS))
        .isInstanceOf(FormatConfigurationException.class)
        .hasMessageContaining("section [*]")
        .hasMessageContaining("ward_quote_style");
  }

  @Test
  void wrongTypeIsRejected() throws IOException {
    write("[*]\nward_format = maybe\n");
    assertThatThrownBy(() -> resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS))
        .isInstanceOf(FormatConfigurationException.class)
        .hasMessageContaining("ward_format");
  }

  @Test
  void wrongNumberIsRejected() throws IOException {
    write("[*]\nindent_size = 0\n");
    assertThatThrownBy(() -> resolve(JAVA_FILE, AnalysisLanguage.JAVA, JAVA_OPTIONS))
        .isInstanceOf(FormatConfigurationException.class)
        .hasMessageContaining("indent_size");
  }

  @Test
  void declaredOptionWithAllowedValuesRejectsOthers() throws IOException {
    final FormatOption style =
        new FormatOption(
            "quote-style", FormatOptionType.TEXT, "double", List.of("double", "single"));
    write("[*]\nward_quote_style = backtick\n");
    assertThatThrownBy(
            () -> resolve("app.ts", AnalysisLanguage.TYPESCRIPT, with(TYPESCRIPT_OPTIONS, style)))
        .isInstanceOf(FormatConfigurationException.class)
        .hasMessageContaining("double, single");
  }
}
