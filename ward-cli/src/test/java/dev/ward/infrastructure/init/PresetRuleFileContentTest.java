package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresetRuleFileContentTest {
  private static final String HIDDEN_FIELD =
      "[rules.java.hidden-field]\nignore-constructor-parameter";

  @Test
  void javaStrictSetsTheHiddenFieldOptions() {
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STRICT, List.of("java")))
        .contains(HIDDEN_FIELD);
  }

  @Test
  void nodeStrictOmitsTheJavaHiddenFieldOptions() {
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STRICT, List.of("typescript")))
        .doesNotContain("hidden-field");
  }

  @Test
  void javaStandardOmitsTheHiddenFieldOptions() {
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STANDARD, List.of("java")))
        .doesNotContain("hidden-field");
  }
}
