package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.UnitScopes;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Set;

/** Builds the scopes of C# source for tests. */
final class CSharpScopeFixtures {
  private static final int ONE = 1;

  private CSharpScopeFixtures() {}

  /** Parses {@code source} and builds its scopes, with no built-in names. */
  static UnitScopes scopes(final String source) {
    final SourceUnit unit = new SourceUnit("m.cs", AnalysisLanguage.CSHARP, source);
    return new CSharpScopeAnalysis(environments -> Set.of())
        .analyze(unit, new AntlrLanguageFrontend().parse(unit).tree());
  }

  /** Returns the one read of {@code name} on {@code line}. */
  static NameReference read(final UnitScopes scopes, final String name, final int line) {
    final List<NameReference> found =
        scopes.references().stream()
            .filter(reference -> reference.name().equals(name))
            .filter(reference -> reference.span().startLine() == line)
            .filter(reference -> reference.access() == ReferenceAccess.READ)
            .toList();
    if (found.size() != ONE) {
      throw new IllegalStateException(name + " on line " + line + ": " + found);
    }
    return found.get(0);
  }

  /** Returns the binding that the read of {@code name} on {@code line} resolves to. */
  static Binding target(final UnitScopes scopes, final String name, final int line) {
    return read(scopes, name, line).resolved().orElseThrow();
  }
}
