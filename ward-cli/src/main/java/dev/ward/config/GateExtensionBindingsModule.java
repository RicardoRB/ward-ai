package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.application.gate.AnalysisPorts;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.application.gate.AnalysisSourceUnits;
import dev.ward.application.gate.GateExtensions;
import dev.ward.application.gate.openapi.OpenApiGates;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import dev.ward.infrastructure.rule.NoRuleMatcher;
import java.util.List;

/** Binds the adapters of the {@code ward} gate and the optional gate extensions. */
@Module
interface GateExtensionBindingsModule {
  @Binds
  AnalysisRuleSource analysisRuleSource(ClasspathRuleSource implementation);

  @Binds
  AnalysisSourceUnits analysisSourceUnits(FileSystemSourceUnits implementation);

  @Binds
  RuleMatcher ruleMatcher(NoRuleMatcher implementation);

  /** No language frontend ships yet, so the ward gate runs no rules. */
  @Provides
  static AnalysisPorts analysisPorts(
      final AnalysisRuleSource rules,
      final RuleMatcher matcher,
      final AnalysisSourceUnits sources) {
    return new AnalysisPorts(rules, matcher, sources, List.of());
  }

  @Provides
  static GateExtensions gateExtensions(final OpenApiGates openApi, final AnalysisPorts analysis) {
    return new GateExtensions(openApi, analysis);
  }
}
