package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.application.gate.AnalysisPorts;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.application.gate.AnalysisSourceUnits;
import dev.ward.application.gate.GateExtensions;
import dev.ward.application.gate.openapi.OpenApiGates;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import java.util.List;
import java.util.Set;

/** Binds the adapters of the {@code ward} gate and the optional gate extensions. */
@Module
interface GateExtensionBindingsModule {
  @Binds
  AnalysisRuleSource analysisRuleSource(ClasspathRuleSource implementation);

  @Binds
  AnalysisSourceUnits analysisSourceUnits(FileSystemSourceUnits implementation);

  @Binds
  RuleMatcher ruleMatcher(DeclarativeRuleMatcher implementation);

  /** The ward gate reads Java sources through the ANTLR frontend; no other language ships yet. */
  @Provides
  static AnalysisPorts analysisPorts(
      final AnalysisRuleSource rules,
      final RuleMatcher matcher,
      final AnalysisSourceUnits sources) {
    final FrontendSupport java =
        new FrontendSupport(
            AnalysisLanguage.JAVA, new AntlrLanguageFrontend(), Set.of(RuleCapability.SYNTAX));
    return new AnalysisPorts(rules, matcher, sources, List.of(java));
  }

  @Provides
  static GateExtensions gateExtensions(final OpenApiGates openApi, final AnalysisPorts analysis) {
    return new GateExtensions(openApi, analysis);
  }
}
