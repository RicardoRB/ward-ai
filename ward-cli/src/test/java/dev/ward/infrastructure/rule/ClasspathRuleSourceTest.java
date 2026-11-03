package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.application.gate.AnalysisRuleLoad;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClasspathRuleSourceTest {

  private static final int JAVA_RULE_COUNT = 13;

  @Test
  void malformedFileIsReportedAndOtherFilesStillLoad() {
    final AnalysisRuleLoad load =
        new ClasspathRuleSource(
                List.of("fixtures/rules/malformed.toml", "fixtures/rules/valid.toml"))
            .load();

    assertThat(load.configurationErrors())
        .singleElement()
        .asString()
        .contains("fixtures/rules/malformed.toml")
        .contains("not valid TOML");
    assertThat(load.rules())
        .extracting(RuleDefinition::id)
        .containsExactly("ward:java/method-length");
  }

  @Test
  void ruleWithUnknownCheckIsReportedAndSkipped() {
    final AnalysisRuleLoad load =
        new ClasspathRuleSource(List.of("fixtures/rules/bad-body.toml")).load();

    assertThat(load.rules()).isEmpty();
    assertThat(load.configurationErrors())
        .singleElement()
        .asString()
        .contains("fixtures/rules/bad-body.toml")
        .contains("unknown check 'no-such-check'");
  }

  @Test
  void packagedJavaRuleFilesLoadAllThirteenRules() {
    final AnalysisRuleLoad load = new ClasspathRuleSource().load();

    assertThat(load.configurationErrors()).isEmpty();
    assertThat(load.rules())
        .extracting(RuleDefinition::id)
        .hasSize(JAVA_RULE_COUNT)
        .allMatch(id -> id.startsWith("ward:java/"));
    assertThat(load.rules())
        .allSatisfy(
            rule -> {
              assertThat(rule.language()).isEqualTo(AnalysisLanguage.JAVA);
              assertThat(rule.capability()).isEqualTo(RuleCapability.SYNTAX);
              assertThat(rule.severity()).isEqualTo(AnalysisSeverity.ERROR);
            });
  }

  @Test
  void missingFileIsReportedAsConfigurationError() {
    final AnalysisRuleLoad load =
        new ClasspathRuleSource(List.of("fixtures/rules/absent.toml")).load();

    assertThat(load.rules()).isEmpty();
    assertThat(load.configurationErrors())
        .singleElement()
        .asString()
        .contains("fixtures/rules/absent.toml")
        .contains("missing");
  }

  @Test
  void duplicateIdAcrossFilesSkipsTheLaterFile() {
    final AnalysisRuleLoad load =
        new ClasspathRuleSource(List.of("fixtures/rules/valid.toml", "fixtures/rules/valid.toml"))
            .load();

    assertThat(load.rules()).hasSize(1);
    assertThat(load.configurationErrors()).singleElement().asString().contains("more than once");
  }
}
