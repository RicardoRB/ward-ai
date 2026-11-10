package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.Set;

/** Builds the scopes of Java and Kotlin source for tests. */
final class JvmScopeFixtures {

  private JvmScopeFixtures() {}

  /** Parses {@code source} as {@code language} and builds its scopes, with no built-in names. */
  static UnitScopes scopes(final AnalysisLanguage language, final String source) {
    final SourceUnit unit = new SourceUnit("m", language, source);
    final ScopeAnalysis analysis =
        language == AnalysisLanguage.KOTLIN
            ? new KotlinScopeAnalysis(environments -> Set.of())
            : new JavaScopeAnalysis(environments -> Set.of());
    return analysis.analyze(unit, new AntlrLanguageFrontend().parse(unit).tree());
  }

  /** Builds the scopes of Java source. */
  static UnitScopes java(final String source) {
    return scopes(AnalysisLanguage.JAVA, source);
  }

  /** Builds the scopes of Kotlin source. */
  static UnitScopes kotlin(final String source) {
    return scopes(AnalysisLanguage.KOTLIN, source);
  }
}
