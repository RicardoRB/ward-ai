package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.metric.MetricMatcher;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.analysis.infrastructure.node.NodeRuleMatcher;
import dev.ward.application.gate.AnalysisPorts;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.application.gate.AnalysisSourceUnits;
import dev.ward.application.gate.GateExtensions;
import dev.ward.application.gate.openapi.OpenApiGates;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.FileSystemSourceUnits;
import dev.ward.infrastructure.rule.LanguageRuleMatcher;
import java.util.List;
import java.util.Set;

/** Binds the adapters of the {@code ward} gate and the optional gate extensions. */
@Module
interface GateExtensionBindingsModule {
  @Binds
  AnalysisRuleSource analysisRuleSource(ClasspathRuleSource implementation);

  @Binds
  AnalysisSourceUnits analysisSourceUnits(FileSystemSourceUnits implementation);

  /**
   * Builds the declarative rules. TypeScript and JavaScript rules use their structural matcher;
   * every other language uses the shared metric matcher.
   */
  @Provides
  static RuleMatcher ruleMatcher() {
    return new LanguageRuleMatcher(new NodeRuleMatcher(), new MetricMatcher());
  }

  /**
   * The ward gate reads TypeScript, JavaScript and Python sources through the ANTLR frontend, with
   * syntax rules only. Gates run one after another, so one frontend, which is not thread-safe,
   * serves them all.
   */
  @Provides
  static AnalysisPorts analysisPorts(
      final AnalysisRuleSource rules,
      final RuleMatcher matcher,
      final AnalysisSourceUnits sources) {
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    final Set<RuleCapability> syntax = Set.of(RuleCapability.SYNTAX);
    final List<FrontendSupport> frontends =
        List.of(
            new FrontendSupport(AnalysisLanguage.PYTHON, frontend, syntax),
            new FrontendSupport(AnalysisLanguage.TYPESCRIPT, frontend, syntax),
            new FrontendSupport(AnalysisLanguage.JAVASCRIPT, frontend, syntax));
    return new AnalysisPorts(rules, matcher, sources, frontends);
  }

  @Provides
  static GateExtensions gateExtensions(final OpenApiGates openApi, final AnalysisPorts analysis) {
    return new GateExtensions(openApi, analysis);
  }
}
