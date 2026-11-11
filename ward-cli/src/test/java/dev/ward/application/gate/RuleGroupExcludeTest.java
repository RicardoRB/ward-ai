package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The group and rule forms of {@code exclude}, and the rule names that stay distinct from groups.
 */
class RuleGroupExcludeTest {

  private static final String INJECT = "ward:java/inject";
  private static final String CRYPTO = "ward:java/crypto";
  private static final String INJECTION = "security/injection";
  private static final String CRYPTO_GROUP = "security/crypto";
  private static final String SECURITY = "security";
  private static final String STANDARD = "standard";
  private static final String IMPORTS = "style/imports";
  private static final String JAVA_IMPORTS = "ward:java/imports";
  private static final String BLOCKS = "ward:java/blocks";

  @Test
  void excludeOfAGroupRemovesEveryRuleOfTheGroup() {
    final List<RuleDefinition> catalog =
        List.of(
            GroupRuleFixtures.withPresets(JAVA_IMPORTS, IMPORTS, RulePreset.STANDARD),
            GroupRuleFixtures.withPresets(BLOCKS, "style/blocks", RulePreset.STANDARD));

    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(STANDARD, List.of(), List.of(), List.of(), List.of(IMPORTS)),
            catalog);

    assertThat(result.ids()).containsExactly(BLOCKS);
  }

  @Test
  void excludeOfAGroupAndARuleInOneList() {
    final List<RuleDefinition> catalog =
        List.of(
            GroupRuleFixtures.withPresets(JAVA_IMPORTS, IMPORTS, RulePreset.STANDARD),
            GroupRuleFixtures.withPresets("ward:java/hidden-field", "style", RulePreset.STANDARD),
            GroupRuleFixtures.withPresets(BLOCKS, "style/blocks", RulePreset.STANDARD));

    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(
                STANDARD, List.of(), List.of(), List.of(), List.of(IMPORTS, "java.hidden-field")),
            catalog);

    assertThat(result.ids()).containsExactly(BLOCKS);
  }

  @Test
  void excludeWinsOverIncludeOfTheSameGroup() {
    final List<RuleDefinition> catalog =
        List.of(
            GroupRuleFixtures.withPresets(INJECT, INJECTION, RulePreset.STRICT),
            GroupRuleFixtures.withPresets(CRYPTO, CRYPTO_GROUP, RulePreset.STRICT));

    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(
                null, List.of(), List.of(), List.of(SECURITY), List.of(CRYPTO_GROUP)),
            catalog);

    assertThat(result.ids()).containsExactly(INJECT);
  }

  @Test
  void aDottedValueIsARuleAndAPlainValueIsAGroup() {
    final List<RuleDefinition> catalog =
        List.of(
            GroupRuleFixtures.withPresets("ward:java/star-import", IMPORTS, RulePreset.STANDARD),
            GroupRuleFixtures.withPresets(BLOCKS, "style/blocks", RulePreset.STANDARD));

    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(
                STANDARD, List.of(), List.of(), List.of(), List.of("java.star-import")),
            catalog);

    assertThat(result.ids()).containsExactly(BLOCKS);
  }
}
