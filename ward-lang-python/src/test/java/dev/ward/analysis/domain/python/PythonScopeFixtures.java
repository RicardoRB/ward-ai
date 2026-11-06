package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.Set;

/** Builds the scopes of Python source for tests. */
final class PythonScopeFixtures {

  private PythonScopeFixtures() {}

  /** Parses {@code source} and builds its scopes, with no built-in names. */
  static UnitScopes scopes(final String source) {
    final SourceUnit unit = new SourceUnit("m.py", AnalysisLanguage.PYTHON, source);
    return new PythonScopes(environments -> Set.of())
        .analyze(unit, new AntlrLanguageFrontend().parse(unit).tree());
  }
}
