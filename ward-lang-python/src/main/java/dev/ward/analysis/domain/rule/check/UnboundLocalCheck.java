package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a name read in a function before the function assigns it, when the function also assigns
 * it later, like pyflakes {@code F823}: the read sees no local value.
 */
public final class UnboundLocalCheck implements RuleCheck {

  private static final String NAME = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public UnboundLocalCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unbound-local check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes(Set.of());
    final List<RuleMatch> found = new ArrayList<>();
    for (final NameReference reference : scopes.references()) {
      if (reference.beforeDeclaration()
          && reference.access() == ReferenceAccess.READ
          && kindOf(scopes, reference.scope()) == ScopeKind.FUNCTION
          && assignedLocally(scopes, reference)) {
        found.add(new RuleMatch(reference.span(), message.replace(NAME, reference.name())));
      }
    }
    return found;
  }

  private static ScopeKind kindOf(final UnitScopes scopes, final int id) {
    return scopes.scopes().stream()
        .filter(scope -> scope.id() == id)
        .map(Scope::kind)
        .findFirst()
        .orElse(ScopeKind.MODULE);
  }

  /** Whether the reference's own scope binds the name as a variable. */
  private static boolean assignedLocally(final UnitScopes scopes, final NameReference reference) {
    return scopes.scopes().stream()
        .filter(scope -> scope.id() == reference.scope())
        .flatMap(scope -> scope.bindings().stream())
        .anyMatch(binding -> isVariable(binding, reference.name()));
  }

  private static boolean isVariable(final Binding binding, final String name) {
    return binding.name().equals(name) && binding.kind() == BindingKind.VARIABLE;
  }
}
