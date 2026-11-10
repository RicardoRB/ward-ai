package dev.ward.analysis.application.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class RuleDefinitionsTest {

  @Test
  void rejectsTwoRulesWithTheSameId() {
    final List<RuleDefinition> rules =
        List.of(rule("ward:java/method-length"), rule("ward:java/method-length"));

    assertThatThrownBy(() -> RuleDefinitions.requireUnique(rules))
        .isInstanceOf(RuleConfigurationException.class)
        .hasMessageContaining("ward:java/method-length")
        .hasMessageContaining("more than once");
  }

  @Test
  void keepsDistinctIdsInOrder() {
    final List<RuleDefinition> rules =
        List.of(rule("ward:java/method-length"), rule("ward:java/field-count"));

    assertThat(RuleDefinitions.requireUnique(rules)).containsExactlyElementsOf(rules);
  }

  @Test
  void builtInTableRowsAreUniqueAndNamedByLanguage() {
    assertThat(RuleDefinitions.requireUnique(RuleDefinitions.all()))
        .allSatisfy(
            rule ->
                assertThat(rule.id())
                    .startsWith(
                        "ward:"
                            + (rule.languages().size() > 1
                                ? RuleDefinition.CROSS_LANGUAGE
                                : rule.languages()
                                    .iterator()
                                    .next()
                                    .name()
                                    .toLowerCase(Locale.ROOT))
                            + "/"));
  }

  private static RuleDefinition rule(final String id) {
    return new RuleDefinition(
        id, AnalysisLanguage.JAVA, RuleCapability.SYNTAX, AnalysisSeverity.ERROR, "body");
  }
}
