package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LanguageFilteredSetupTest {

  private static final String RULE = "ward:openapi/operation-operation-id";

  private final List<RuleDefinition> all = new ClasspathRuleSource().load().rules();
  private final RuleSetup everything = (root, release) -> RuleSet.of(all);

  @Test
  void theWardGateDropsTheOpenApiRulesAfterConfiguration() {
    final RuleSet set = LanguageFilteredSetup.withoutOpenApi(everything).resolve(Path.of("."), 0);

    assertThat(all).extracting(RuleDefinition::id).contains(RULE);
    assertThat(set.rules()).extracting(RuleDefinition::id).doesNotContain(RULE).isNotEmpty();
  }

  @Test
  void theLintGateKeepsOnlyTheOpenApiRules() {
    final RuleSet set = LanguageFilteredSetup.onlyOpenApi(everything).resolve(Path.of("."), 0);

    assertThat(set.rules())
        .extracting(RuleDefinition::id)
        .contains(RULE)
        .allSatisfy(id -> assertThat(id).startsWith("ward:openapi/"));
  }

  @Test
  void problemsAndExcludesPassThrough() {
    final RuleSetup broken =
        (root, release) -> new RuleSet(all, java.util.Map.of(), List.of("bad"));

    assertThat(LanguageFilteredSetup.onlyOpenApi(broken).resolve(Path.of("."), 0).problems())
        .containsExactly("bad");
  }
}
