package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The group forms of {@code categories} and {@code include}. */
class RuleGroupSelectionTest {

  private static final String INJECT = "ward:java/inject";
  private static final String CRYPTO = "ward:java/crypto";
  private static final String PLAIN = "ward:java/plain";
  private static final String INJECTION = "security/injection";
  private static final String CRYPTO_GROUP = "security/crypto";
  private static final String SECURITY = "security";
  private static final String STANDARD = "standard";
  private static final String IMPORTS = "style/imports";
  private static final String NAMING = "ward:java/naming";
  private static final String NAMING_GROUP = "naming";
  private static final String SIZE = "ward:java/size";
  private static final String SIZE_GROUP = "complexity/size";

  @Test
  void aSubcategoryNarrowsCategoriesToItsOwnRules() {
    final List<RuleDefinition> catalog =
        List.of(
            GroupRuleFixtures.withPresets(INJECT, INJECTION, RulePreset.STANDARD),
            GroupRuleFixtures.withPresets(CRYPTO, CRYPTO_GROUP, RulePreset.STANDARD));

    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(
                STANDARD, List.of(), List.of(INJECTION), List.of(), List.of()),
            catalog);

    assertThat(result.ids()).containsExactly(INJECT);
    assertThat(result.problems()).isEmpty();
  }

  @Test
  void aFamilyCategoryKeepsItsSubcategories() {
    final List<RuleDefinition> catalog =
        List.of(
            GroupRuleFixtures.withPresets(INJECT, INJECTION, RulePreset.STANDARD),
            GroupRuleFixtures.withPresets(PLAIN, SECURITY, RulePreset.STANDARD),
            GroupRuleFixtures.withPresets("ward:java/style", IMPORTS, RulePreset.STANDARD));

    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(null, List.of(), List.of(SECURITY), List.of(), List.of()),
            catalog);

    assertThat(result.ids()).containsExactlyInAnyOrder(INJECT, PLAIN);
    assertThat(result.problems()).isEmpty();
  }

  @Test
  void anUnknownGroupListsEveryValidGroupName() {
    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(
                null, List.of(), List.of("style/unknown"), List.of(), List.of()),
            List.of(GroupRuleFixtures.categorised(INJECT, AnalysisLanguage.JAVA, INJECTION)));

    assertThat(result.problems())
        .singleElement()
        .asString()
        .contains(
            "unknown group \"style/unknown\"; expected one of [security, security/injection]");
  }

  @Test
  void includeOfAGroupAddsRulesOutsideThePreset() {
    final List<RuleDefinition> catalog =
        List.of(
            GroupRuleFixtures.withPresets(INJECT, INJECTION, RulePreset.STRICT),
            GroupRuleFixtures.withPresets(PLAIN, NAMING_GROUP, RulePreset.STANDARD));

    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(STANDARD, List.of(), List.of(), List.of(SECURITY), List.of()),
            catalog);

    assertThat(result.ids()).containsExactlyInAnyOrder(INJECT, PLAIN);
  }

  @Test
  void includeOfAGroupBuildsASetFromGroupsOnPresetNone() {
    final List<RuleDefinition> catalog =
        List.of(
            GroupRuleFixtures.withPresets(NAMING, NAMING_GROUP, RulePreset.STANDARD),
            GroupRuleFixtures.withPresets(SIZE, SIZE_GROUP, RulePreset.STANDARD),
            GroupRuleFixtures.withPresets(
                "ward:java/metric", "complexity/metrics", RulePreset.STANDARD));

    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(
                "none", List.of(), List.of(), List.of(NAMING_GROUP, SIZE_GROUP), List.of()),
            catalog);

    assertThat(result.ids()).containsExactlyInAnyOrder(NAMING, SIZE);
  }

  @Test
  void includeOfAGroupRespectsLanguages() {
    final List<RuleDefinition> catalog =
        List.of(
            GroupRuleFixtures.withPresets(INJECT, INJECTION, RulePreset.STANDARD),
            GroupRuleFixtures.categorised(
                "ward:python/inject", AnalysisLanguage.PYTHON, INJECTION));

    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(
                "none", List.of("java"), List.of(), List.of(SECURITY), List.of()),
            catalog);

    assertThat(result.ids()).containsExactly(INJECT);
  }
}
