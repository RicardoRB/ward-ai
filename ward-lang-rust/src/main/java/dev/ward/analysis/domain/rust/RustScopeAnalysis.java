package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.BuiltinNames;
import dev.ward.analysis.domain.port.ScopeAnalysis;
import dev.ward.analysis.domain.scope.ClassScopes;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.Set;

/**
 * The lexical scopes of a Rust file. Every {@code let} starts a scope that lasts to the end of its
 * block, so a later {@code let} of the same name shadows the earlier one and an initializer reads
 * the earlier binding. Items of a block are visible in the whole block. A match arm, a closure, a
 * {@code for} loop and an {@code if let} or {@code while let} each have a scope of their own. An
 * identifier pattern that starts with an upper-case letter names a constant or a variant, as
 * convention (and the compiler's lint) require, so it is a reference and not a binding. Methods
 * are not names in scope, and a glob import makes the scope open. Names that a macro defines are
 * not seen.
 */
public final class RustScopeAnalysis implements ScopeAnalysis {
  private final BuiltinNames builtins;

  /** Creates the analysis over the names the Rust prelude provides. */
  public RustScopeAnalysis(final BuiltinNames builtins) {
    this.builtins = builtins;
  }

  @Override
  public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
    final ScopeBuilder builder = new ScopeBuilder(tree.span(), ClassScopes.VISIBLE);
    new RustScopeWalk(builder).visit(tree);
    return builder.build();
  }

  @Override
  public Set<String> builtins(final Set<String> environments) {
    return builtins.of(environments);
  }
}
