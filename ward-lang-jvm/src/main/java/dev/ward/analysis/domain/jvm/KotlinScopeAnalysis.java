package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.BuiltinNames;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.Set;

/**
 * The lexical scopes of a Kotlin file. Receivers and inherited members are not visible to the
 * analysis, so extension functions, receiver lambdas and classes with supertypes are open.
 */
public final class KotlinScopeAnalysis implements ScopeAnalysis {

  private final BuiltinNames builtins;

  /** Creates the analysis over the names the Kotlin standard library imports by default. */
  public KotlinScopeAnalysis(final BuiltinNames builtins) {
    this.builtins = builtins;
  }

  @Override
  public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
    final ScopeBuilder builder = new ScopeBuilder(tree.span(), ClassScopes.VISIBLE);
    new KotlinScopeWalk(builder).visit(tree);
    return builder.build();
  }

  @Override
  public Set<String> builtins(final Set<String> environments) {
    return builtins.of(environments);
  }
}
