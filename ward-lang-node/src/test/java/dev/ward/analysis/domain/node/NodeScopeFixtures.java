package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.Set;

/** Builds the scopes of JavaScript and TypeScript source for tests. */
final class NodeScopeFixtures {

  private NodeScopeFixtures() {}

  /** Parses {@code source} as {@code language} and builds its scopes, with no built-in names. */
  static UnitScopes scopes(final AnalysisLanguage language, final String source) {
    final SourceUnit unit = new SourceUnit("m", language, source);
    return new NodeScopeAnalysis(environments -> Set.of())
        .analyze(unit, new AntlrLanguageFrontend().parse(unit).tree());
  }

  /** Builds the scopes of TypeScript source. */
  static UnitScopes typescript(final String source) {
    return scopes(AnalysisLanguage.TYPESCRIPT, source);
  }

  /** Builds the scopes of TSX source, which recognizes JSX. */
  static UnitScopes tsx(final String source) {
    final SourceUnit unit = new SourceUnit("m.tsx", AnalysisLanguage.TYPESCRIPT, source);
    return new NodeScopeAnalysis(environments -> Set.of())
        .analyze(unit, new AntlrLanguageFrontend().parse(unit).tree());
  }

  /** Builds the scopes of JavaScript source. */
  static UnitScopes javascript(final String source) {
    return scopes(AnalysisLanguage.JAVASCRIPT, source);
  }
}
