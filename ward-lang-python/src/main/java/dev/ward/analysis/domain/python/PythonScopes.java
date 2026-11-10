package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.BuiltinNames;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.HashSet;
import java.util.Set;

/**
 * The lexical scopes of a Python file. Names listed in a literal {@code __all__} are exported. A
 * {@code from m import *}, and a call of {@code exec}, {@code eval}, {@code locals} or {@code vars}
 * make a scope open, because they can bring in names that this file does not spell.
 */
public final class PythonScopes implements ScopeAnalysis {

  private final BuiltinNames builtins;

  /** Creates the analysis over the builtin names of Python. */
  public PythonScopes(final BuiltinNames builtins) {
    this.builtins = builtins;
  }

  @Override
  public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
    final ScopeBuilder builder = new ScopeBuilder(tree.span(), ClassScopes.SKIPPED);
    final Set<String> exported =
        new HashSet<>(PythonExports.declaredAll(tree).orElse(java.util.List.of()));
    final PythonScopeWalk walk = new PythonScopeWalk(builder, exported);
    walk.visit(tree);
    return builder.build();
  }

  @Override
  public Set<String> builtins(final Set<String> environments) {
    return builtins.of(environments);
  }
}
