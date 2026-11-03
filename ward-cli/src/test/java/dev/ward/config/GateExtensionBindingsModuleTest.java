package dev.ward.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.rule.metric.MetricMatcher;
import dev.ward.application.gate.AnalysisPorts;
import dev.ward.application.gate.AnalysisResultCodec;
import dev.ward.application.gate.AnalysisRuleLoad;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.application.gate.AnalysisSourceUnits;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The analysis frontends the CLI binds, so every language with rules has a parser for them. */
class GateExtensionBindingsModuleTest {

  @Test
  void bindsAFrontendForEachAnalysisLanguageWithRules() {
    final AnalysisRuleSource rules = () -> new AnalysisRuleLoad(List.of(), List.of());

    final AnalysisPorts ports =
        GateExtensionBindingsModule.analysisPorts(
            rules, new MetricMatcher(), sources(), codec(), "test");

    assertThat(ports.frontends())
        .extracting(FrontendSupport::language)
        .contains(
            AnalysisLanguage.JAVA,
            AnalysisLanguage.PYTHON,
            AnalysisLanguage.TYPESCRIPT,
            AnalysisLanguage.JAVASCRIPT,
            AnalysisLanguage.SWIFT,
            AnalysisLanguage.KOTLIN);
  }

  private static AnalysisResultCodec codec() {
    return new AnalysisResultCodec() {
      @Override
      public String encode(final String key, final List<AnalysisFinding> findings) {
        return "";
      }

      @Override
      public Optional<List<AnalysisFinding>> decode(final String key, final String value) {
        return Optional.empty();
      }

      @Override
      public String encodeSummary(final String key, final FileSummary summary) {
        return "";
      }

      @Override
      public Optional<FileSummary> decodeSummary(final String key, final String value) {
        return Optional.empty();
      }

      @Override
      public String encodeIndex(final DependencyGraph graph) {
        return "";
      }
    };
  }

  private static AnalysisSourceUnits sources() {
    return (root, languages) -> List.of();
  }
}
