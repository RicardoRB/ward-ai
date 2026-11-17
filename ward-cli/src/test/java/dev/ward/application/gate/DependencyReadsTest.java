package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What a unit's key holds, by the capabilities of the Java rules that run. */
class DependencyReadsTest {
  private static final List<RuleDefinition> SHIPPED = new ClasspathRuleSource().load().rules();

  private static List<RuleDefinition> java(final RuleCapability capability) {
    return SHIPPED.stream()
        .filter(rule -> rule.covers(AnalysisLanguage.JAVA))
        .filter(rule -> rule.capability() == capability)
        .limit(2)
        .toList();
  }

  private static boolean names(final RuleCapability... capabilities) {
    final List<RuleDefinition> rules = new ArrayList<>();
    for (final RuleCapability capability : capabilities) {
      rules.addAll(java(capability));
    }
    assertThat(rules).isNotEmpty();
    return DependencyReads.names(AnalysisLanguage.JAVA, rules);
  }

  @Test
  void taintRulesAloneKeyOnSummariesAndNotOnTheNamesAroundTheUnit() {
    final List<RuleDefinition> taint = java(RuleCapability.TAINT);

    assertThat(DependencyReads.dependencies(AnalysisLanguage.JAVA, taint)).isTrue();
    assertThat(DependencyReads.bodies(AnalysisLanguage.JAVA, taint)).isTrue();
    assertThat(DependencyReads.names(AnalysisLanguage.JAVA, taint)).isFalse();
  }

  @Test
  void ruleKindsThatReadNamesOrTypeFactsKeepTheNames() {
    assertThat(names(RuleCapability.SCOPES)).isTrue();
    assertThat(names(RuleCapability.TYPES)).isTrue();
    assertThat(names(RuleCapability.TAINT, RuleCapability.TYPES)).isTrue();
  }

  @Test
  void syntaxFlowAndTextRulesNeitherReadNorKeyOnTheFilesAroundTheUnit() {
    assertThat(names(RuleCapability.SYNTAX)).isFalse();
    assertThat(names(RuleCapability.CFG, RuleCapability.DATAFLOW)).isFalse();
    assertThat(DependencyReads.dependencies(AnalysisLanguage.JAVA, java(RuleCapability.SYNTAX)))
        .isFalse();
  }
}
