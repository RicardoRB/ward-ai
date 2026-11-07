package dev.ward.analysis.application.jvm;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.TypeFactsFixture;
import dev.ward.analysis.application.rule.TypeFactsRun;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.jvm.JavaScopeAnalysis;
import dev.ward.analysis.domain.jvm.KotlinScopeAnalysis;
import dev.ward.analysis.domain.port.BuiltinNames;
import dev.ward.analysis.domain.port.ControlFlowResult;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.scope.CachedScopes;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The frontends the fixture runner of this module gives the rule engine. */
final class JvmFixtureFrontends {

  private JvmFixtureFrontends() {}

  static List<FrontendSupport> of(final AnalysisLanguage language) {
    final BuiltinNames none = environments -> Set.of();
    final AntlrLanguageFrontend frontend = new AntlrLanguageFrontend();
    if (language == AnalysisLanguage.KOTLIN) {
      return List.of(
          new FrontendSupport(
              language,
              frontend,
              Set.of(RuleCapability.SYNTAX, RuleCapability.SCOPES),
              (unit, tree) -> new ControlFlowResult(List.of(), List.of()),
              new CachedScopes(new KotlinScopeAnalysis(none))));
    }
    return List.of(
        new FrontendSupport(
            language,
            frontend,
            Set.of(
                RuleCapability.SYNTAX,
                RuleCapability.SYMBOLS,
                RuleCapability.CFG,
                RuleCapability.DATAFLOW,
                RuleCapability.SCOPES),
            new JavaControlFlow(),
            new CachedScopes(new JavaScopeAnalysis(none))));
  }

  /** A rule that needs types gets the facts of the fixture's {@code types.json}. */
  static List<FrontendSupport> forRule(
      final RuleDefinition rule, final AnalysisLanguage language, final Path directory) {
    final List<FrontendSupport> frontends = of(language);
    if (rule.capability() != RuleCapability.TYPES) {
      return frontends;
    }
    final Path types = directory.resolve("types.json");
    final UnitTypeFacts unitFacts =
        Files.isRegularFile(types) ? TypeFactsFixture.read(types) : UnitTypeFacts.none();
    final TypeFacts facts = (unit, spans, kinds) -> unitFacts;
    final Map<AnalysisLanguage, TypeFacts> sources = new EnumMap<>(AnalysisLanguage.class);
    sources.put(language, facts);
    return new TypeFactsRun(sources).withTypes(frontends);
  }
}
