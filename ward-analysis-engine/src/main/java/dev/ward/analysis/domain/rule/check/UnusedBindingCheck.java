package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reports each local variable that is declared or assigned and never read, like pyflakes {@code
 * F841} and ESLint {@code no-unused-vars} for locals. Parameters, imports, members and module-level
 * names are left alone, and so are names that start with {@code _}, names bound by unpacking or a
 * loop header, names in an open scope and names a nested function declares {@code global} or {@code
 * nonlocal}.
 */
public final class UnusedBindingCheck implements RuleCheck {

  private static final String NAME = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public UnusedBindingCheck(final String message) {
    this.message = message;
  }

  /**
   * Always fails: the check reads the scopes of the unit, which only {@link #matches(SyntaxNode,
   * FlowView, ScopeView, SourceUnit)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unused-binding check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      if (UnusedBindings.isLocalScope(scope) && !scope.open()) {
        scope.bindings().stream()
            .filter(binding -> UnusedBindings.isUnused(binding, scopes))
            .forEach(binding -> found.add(match(binding)));
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private RuleMatch match(final Binding binding) {
    return new RuleMatch(binding.declaration(), message.replace(NAME, binding.name()));
  }
}
