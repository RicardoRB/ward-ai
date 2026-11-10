package dev.ward.analysis.domain.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SourceUnitTest {

  @Test
  void keepsSlashPathLanguageAndText() {
    final SourceUnit unit =
        new SourceUnit("src/main/App.java", AnalysisLanguage.JAVA, "class App {}");

    assertThat(unit.path()).isEqualTo("src/main/App.java");
    assertThat(unit.language()).isEqualTo(AnalysisLanguage.JAVA);
    assertThat(unit.text()).isEqualTo("class App {}");
  }

  @Test
  void rejectsBackslashInPath() {
    assertThatThrownBy(() -> new SourceUnit("src\\App.java", AnalysisLanguage.JAVA, ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requiresLanguageAndText() {
    assertThatThrownBy(() -> new SourceUnit("App.java", null, ""))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new SourceUnit("App.java", AnalysisLanguage.JAVA, null))
        .isInstanceOf(NullPointerException.class);
  }
}
