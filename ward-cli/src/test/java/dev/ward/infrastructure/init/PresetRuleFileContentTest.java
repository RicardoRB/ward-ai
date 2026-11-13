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
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STRICT, List.of("java"), false, true))
        .contains(HIDDEN_FIELD);
  }

  @Test
  void nodeStrictOmitsTheJavaHiddenFieldOptions() {
    assertThat(
            PresetRuleFileContent.wardRules(
                QualityPreset.STRICT, List.of("typescript"), false, true))
        .doesNotContain("hidden-field");
  }

  @Test
  void javaStandardOmitsTheHiddenFieldOptions() {
    assertThat(
            PresetRuleFileContent.wardRules(QualityPreset.STANDARD, List.of("java"), false, true))
        .doesNotContain("hidden-field");
  }

  private static final String FOUR =
      "\"build.plugin-version-pinned\", \"build.no-dynamic-version\","
          + " \"build.minimum-build-tool-version\","
          + " \"build.duplicate-dependency-declaration\"";

  @Test
  void strictMavenWithBuildPolicyAlsoSelectsTheInventoryRulesThatReplacedEnforcer() {
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STRICT, List.of("java"), true, true))
        .contains(
            "include = ["
                + FOUR
                + ", \"build.dependency-convergence\", \"build.require-upper-bound\"]");
  }

  @Test
  void strictGradleAndNodeSelectTheFourStaticRulesOnly() {
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STRICT, List.of("java"), true, false))
        .contains("include = [" + FOUR + "]")
        .doesNotContain("dependency-convergence");
    assertThat(
            PresetRuleFileContent.wardRules(
                QualityPreset.STRICT, List.of("typescript"), true, true))
        .contains("include = [" + FOUR + "]")
        .doesNotContain("require-upper-bound");
  }

  @Test
  void standardAndStrictWithoutTheGateSelectNoBuildRules() {
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STANDARD, List.of("java"), true, true))
        .doesNotContain("build.");
    assertThat(PresetRuleFileContent.wardRules(QualityPreset.STRICT, List.of("java"), false, true))
        .doesNotContain("build.");
  }
}
