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
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STRICT, List.of("java"), false))
        .contains(HIDDEN_FIELD);
  }

  @Test
  void nodeStrictOmitsTheJavaHiddenFieldOptions() {
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STRICT, List.of("typescript"), false))
        .doesNotContain("hidden-field");
  }

  @Test
  void javaStandardOmitsTheHiddenFieldOptions() {
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STANDARD, List.of("java"), false))
        .doesNotContain("hidden-field");
  }

  @Test
  void strictWithBuildPolicySelectsTheFourNativeBuildRules() {
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STRICT, List.of("java"), true))
        .contains(
            "include = [\"build.plugin-version-pinned\", \"build.no-dynamic-version\","
                + " \"build.minimum-build-tool-version\","
                + " \"build.duplicate-dependency-declaration\"]")
        .doesNotContain("dependency-convergence");
  }

  @Test
  void standardAndStrictWithoutTheGateSelectNoBuildRules() {
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STANDARD, List.of("java"), true))
        .doesNotContain("build.");
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STRICT, List.of("java"), false))
        .doesNotContain("build.");
  }
}
