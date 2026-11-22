package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.BuiltinNames;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.Set;

/**
 * The lexical scopes of a C# file: members in type scopes, parameters in method and lambda scopes,
 * locals in block scopes. Names that the file does not declare (members of a base type, of a
 * partial part in another file) make their type scope open.
 */
public final class CSharpScopeAnalysis implements ScopeAnalysis {
  private final BuiltinNames builtins;

  /** Creates the analysis over the names C# provides without a declaration. */
  public CSharpScopeAnalysis(final BuiltinNames builtins) {
    this.builtins = builtins;
  }

  @Override
  public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
    final ScopeBuilder builder = new ScopeBuilder(tree.span(), ClassScopes.VISIBLE);
    new CSharpScopeWalk(builder).visit(tree);
    return builder.build();
  }

  @Override
  public Set<String> builtins(final Set<String> environments) {
    return builtins.of(environments);
  }
}
