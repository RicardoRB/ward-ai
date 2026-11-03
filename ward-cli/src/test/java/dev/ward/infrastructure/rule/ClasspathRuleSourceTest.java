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

  private static final int BUNDLED_RULES = 12;
  private static final int RULES_PER_LANGUAGE = 6;

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
        .containsExactly(
            new RuleDefinition(
                "ward:java/method-length",
                AnalysisLanguage.JAVA,
                RuleCapability.SYNTAX,
                AnalysisSeverity.WARNING,
                "method_declaration"));
  }

  @Test
  void bundledNodeRulesLoadWithOneRowPerLanguageAndMetric() {
    final AnalysisRuleLoad load = new ClasspathRuleSource().load();

    assertThat(load.configurationErrors()).isEmpty();
    assertThat(load.rules()).hasSize(BUNDLED_RULES);
    assertThat(load.rules())
        .allSatisfy(
            rule -> {
              assertThat(rule.capability()).isEqualTo(RuleCapability.SYNTAX);
              assertThat(rule.severity()).isEqualTo(AnalysisSeverity.ERROR);
              assertThat(rule.body()).isEmpty();
            });
    assertThat(load.rules().stream().map(RuleDefinition::id).distinct()).hasSize(BUNDLED_RULES);
    assertThat(load.rules().stream().filter(r -> r.language() == AnalysisLanguage.TYPESCRIPT))
        .hasSize(RULES_PER_LANGUAGE);
    assertThat(load.rules().stream().filter(r -> r.language() == AnalysisLanguage.JAVASCRIPT))
        .hasSize(RULES_PER_LANGUAGE);
  }

  @Test
  void rowWhoseIdNamesAnotherLanguageIsRejectedAndTheErrorNamesTheId() {
    final AnalysisRuleLoad load =
        new ClasspathRuleSource(List.of("fixtures/rules/wrong-language.toml")).load();

    assertThat(load.rules()).isEmpty();
    assertThat(load.configurationErrors())
        .singleElement()
        .asString()
        .contains("fixtures/rules/wrong-language.toml")
        .contains("ward:javascript/naming");
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
