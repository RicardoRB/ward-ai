package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.Set;

/**
 * Builds the lexical scopes of the source units of one language, following that language's own
 * scoping rules.
 */
public interface ScopeAnalysis {

  /**
   * Builds the scopes of {@code unit}. The result depends on the unit alone, so it may be cached by
   * the unit's content.
   *
   * @param unit the source unit
   * @param tree the syntax tree of the unit
   * @return the scopes, bindings and resolved references of the unit
   */
  UnitScopes analyze(SourceUnit unit, SyntaxNode tree);

  /**
   * Returns the names the language provides without a declaration.
   *
   * @param environments the environments to include, such as {@code browser} or {@code node}; empty
   *     means the language's defaults
   * @return the built-in names, never null
   * @throws AnalysisFailedException if an environment is not known to the language
   */
  Set<String> builtins(Set<String> environments);
}
