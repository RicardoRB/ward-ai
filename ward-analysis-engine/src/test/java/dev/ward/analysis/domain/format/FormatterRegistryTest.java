package dev.ward.analysis.domain.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormatterRegistryTest {

  private static final StubFormatter JAVA_FORMATTER =
      new StubFormatter(AnalysisLanguage.JAVA, s -> new FormatResult.Unchanged());

  @Test
  void findsFormatterForRegisteredLanguage() {
    final FormatterRegistry registry =
        new FormatterRegistry(Map.of(AnalysisLanguage.JAVA, JAVA_FORMATTER));

    assertThat(registry.find(AnalysisLanguage.JAVA)).contains(JAVA_FORMATTER);
    assertThat(registry.find(AnalysisLanguage.PYTHON)).isEmpty();
  }

  @Test
  void listsRegisteredLanguagesAsReadOnly() {
    final FormatterRegistry registry =
        new FormatterRegistry(Map.of(AnalysisLanguage.JAVA, JAVA_FORMATTER));

    assertThat(registry.languages()).containsExactly(AnalysisLanguage.JAVA);
    assertThatThrownBy(() -> registry.languages().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsFormatterForAnotherLanguage() {
    assertThatThrownBy(() -> new FormatterRegistry(Map.of(AnalysisLanguage.PYTHON, JAVA_FORMATTER)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
