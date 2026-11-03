package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisCacheKeyTest {
  private static final String VERSION = "0.6.0";
  private static final RuleDefinition LENGTH =
      new RuleDefinition(
          "ward:java/method-length",
          AnalysisLanguage.JAVA,
          RuleCapability.SYNTAX,
          AnalysisSeverity.ERROR,
          "body");

  @Test
  void ruleSetHashIgnoresTheOrderOfRules() {
    final RuleDefinition naming =
        new RuleDefinition(
            "ward:java/naming",
            AnalysisLanguage.JAVA,
            RuleCapability.SYNTAX,
            AnalysisSeverity.WARNING,
            "other");

    assertThat(AnalysisCacheKey.ruleSet(VERSION, List.of(LENGTH, naming)))
        .isEqualTo(AnalysisCacheKey.ruleSet(VERSION, List.of(naming, LENGTH)));
  }

  @Test
  void ruleSetHashChangesWithBodySeverityCapabilityOrVersion() {
    final String base = AnalysisCacheKey.ruleSet(VERSION, List.of(LENGTH));

    assertThat(AnalysisCacheKey.ruleSet(VERSION, List.of(withBody("changed")))).isNotEqualTo(base);
    assertThat(
            AnalysisCacheKey.ruleSet(
                VERSION,
                List.of(
                    new RuleDefinition(
                        LENGTH.id(),
                        AnalysisLanguage.JAVA,
                        RuleCapability.SYNTAX,
                        AnalysisSeverity.WARNING,
                        "body"))))
        .isNotEqualTo(base);
    assertThat(
            AnalysisCacheKey.ruleSet(
                VERSION,
                List.of(
                    new RuleDefinition(
                        LENGTH.id(),
                        AnalysisLanguage.JAVA,
                        RuleCapability.CFG,
                        AnalysisSeverity.ERROR,
                        "body"))))
        .isNotEqualTo(base);
    assertThat(AnalysisCacheKey.ruleSet("0.6.1", List.of(LENGTH))).isNotEqualTo(base);
  }

  @Test
  void identicalTextUnderDifferentPathsHasDifferentKeys() {
    final String rules = AnalysisCacheKey.ruleSet(VERSION, List.of(LENGTH));

    assertThat(AnalysisCacheKey.unit(rules, unit("src/A.java", "class A {}")))
        .isNotEqualTo(AnalysisCacheKey.unit(rules, unit("src/B.java", "class A {}")));
  }

  @Test
  void splitBetweenPathAndTextDoesNotCollide() {
    final String rules = AnalysisCacheKey.ruleSet(VERSION, List.of(LENGTH));

    assertThat(AnalysisCacheKey.unit(rules, unit("a", "bc")))
        .isNotEqualTo(AnalysisCacheKey.unit(rules, unit("ab", "c")));
  }

  private static RuleDefinition withBody(final String body) {
    return new RuleDefinition(
        LENGTH.id(), AnalysisLanguage.JAVA, RuleCapability.SYNTAX, AnalysisSeverity.ERROR, body);
  }

  private static SourceUnit unit(final String path, final String text) {
    return new SourceUnit(path, AnalysisLanguage.JAVA, text);
  }
}
