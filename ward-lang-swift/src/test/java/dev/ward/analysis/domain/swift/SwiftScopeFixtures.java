package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.Set;

/** Builds the scopes of Swift source for tests. */
final class SwiftScopeFixtures {

  private SwiftScopeFixtures() {}

  /** Parses {@code source} and builds its scopes, with no built-in names. */
  static UnitScopes scopes(final String source) {
    final SourceUnit unit = new SourceUnit("m.swift", AnalysisLanguage.SWIFT, source);
    return new SwiftScopeAnalysis(environments -> Set.of())
        .analyze(unit, new AntlrLanguageFrontend().parse(unit).tree());
  }
}
