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
 * The lexical scopes of a Java file. Types and members of other files are not visible to the
 * analysis, so a class that inherits makes its scope open, and an upper-case name is left to the
 * rules that report undefined names.
 */
public final class JavaScopeAnalysis implements ScopeAnalysis {

  private final BuiltinNames builtins;

  /** Creates the analysis over the names {@code java.lang} provides. */
  public JavaScopeAnalysis(final BuiltinNames builtins) {
    this.builtins = builtins;
  }

  @Override
  public UnitScopes analyze(final SourceUnit unit, final SyntaxNode tree) {
    final ScopeBuilder builder = new ScopeBuilder(tree.span(), ClassScopes.VISIBLE);
    new JavaScopeWalk(builder).visit(tree);
    return builder.build();
  }

  @Override
  public Set<String> builtins(final Set<String> environments) {
    return builtins.of(environments);
  }
}
