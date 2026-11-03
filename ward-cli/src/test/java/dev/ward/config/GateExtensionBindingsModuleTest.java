package dev.ward.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.metric.MetricMatcher;
import dev.ward.application.gate.AnalysisPorts;
import dev.ward.application.gate.AnalysisRuleLoad;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.application.gate.AnalysisSourceUnits;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The analysis frontends the CLI binds, so every language with rules has a parser for them. */
class GateExtensionBindingsModuleTest {

  @Test
  void bindsAFrontendForEachAnalysisLanguageWithRules() {
    final AnalysisRuleSource rules = () -> new AnalysisRuleLoad(List.of(), List.of());

    final AnalysisPorts ports =
        GateExtensionBindingsModule.analysisPorts(rules, new MetricMatcher(), sources());

    assertThat(ports.frontends())
        .extracting(FrontendSupport::language)
        .contains(
            AnalysisLanguage.JAVA,
            AnalysisLanguage.PYTHON,
            AnalysisLanguage.TYPESCRIPT,
            AnalysisLanguage.JAVASCRIPT,
            AnalysisLanguage.SWIFT);
  }

  private static AnalysisSourceUnits sources() {
    return (root, languages) -> List.of();
  }
}
