package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reports a named closure parameter that the closure never uses. It reads the scopes: the text of
 * a closure does not tell a use from a longer name that contains the parameter's name (#567).
 */
public final class SwiftUnusedClosureParameterCheck implements RuleCheck {
  private static final String IGNORED = "_";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of a finding
   */
  public SwiftUnusedClosureParameterCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unused closure parameter check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView view,
      final SourceUnit unit
  ) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      if (scope.kind() == ScopeKind.LAMBDA) {
        scope.bindings().stream()
            .filter(binding -> unused(binding, scopes))
            .forEach(binding -> found.add(new RuleMatch(binding.declaration(), message)));
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private static boolean unused(final Binding binding, final UnitScopes scopes) {
    return binding.kind() == BindingKind.PARAMETER
        && !binding.name().startsWith(IGNORED)
        && scopes.usesOf(binding).isEmpty();
  }
}
