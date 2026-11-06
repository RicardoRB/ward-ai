package dev.ward.analysis.domain.scope;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import java.util.Set;

/** The scope analysis of a language that has none: every unit has empty scopes. */
public enum NoScopes implements ScopeAnalysis {
  /** The only instance. */
  INSTANCE;

  @Override
  public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
    return UnitScopes.EMPTY;
  }

  @Override
  public Set<String> builtins(final Set<String> environments) {
    return Set.of();
  }
}
