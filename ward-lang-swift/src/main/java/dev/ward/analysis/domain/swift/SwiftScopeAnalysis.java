package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.BuiltinNames;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.Set;

/**
 * The lexical scopes of a Swift file. Members of other files, of supertypes and of extended types
 * are not visible to the analysis, so a type with a supertype, an extension and a protocol are
 * open.
 */
public final class SwiftScopeAnalysis implements ScopeAnalysis {

  private final BuiltinNames builtins;

  /** Creates the analysis over the names the Swift standard library provides. */
  public SwiftScopeAnalysis(final BuiltinNames builtins) {
    this.builtins = builtins;
  }

  @Override
  public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
    final ScopeBuilder builder = new ScopeBuilder(tree.span(), ClassScopes.VISIBLE);
    new SwiftScopeWalk(builder).visit(tree);
    return builder.build();
  }

  @Override
  public Set<String> builtins(final Set<String> environments) {
    return builtins.of(environments);
  }
}
