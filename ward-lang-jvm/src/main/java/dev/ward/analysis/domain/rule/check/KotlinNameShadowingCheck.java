package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a local value, variable, lambda parameter or destructuring entry whose name an enclosing
 * block or lambda of the same function already binds, as detekt's {@code no-name-shadowing} does.
 * The walk goes outward through blocks and lambdas and stops at the enclosing function, so names of
 * another function are not compared, and at a class, so members are not compared. A name is only
 * shadowed when the outer declaration comes before the inner one, and {@code _} and the implicit
 * {@code it} are never reported.
 */
public final class KotlinNameShadowingCheck implements RuleCheck {

  private static final Set<ScopeKind> LOCAL_SCOPES =
      Set.of(ScopeKind.BLOCK, ScopeKind.LAMBDA, ScopeKind.FUNCTION);
  private static final Set<BindingKind> VALUE_KINDS =
      Set.of(BindingKind.VARIABLE, BindingKind.CONSTANT, BindingKind.PARAMETER);
  private static final String IGNORED = "_";
  private static final String IMPLICIT_IT = "it";
  private static final int NO_SCOPE = -1;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public KotlinNameShadowingCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /**
   * Always fails: the check reads the scopes of the unit, which only the four-argument overload is
   * given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the name-shadowing check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      if (LOCAL_SCOPES.contains(scope.kind())) {
        for (final Binding binding : scope.bindings()) {
          if (candidate(binding, scope) && shadowsOuter(binding, scope, scopes)) {
            found.add(
                new RuleMatch(binding.declaration(), message.replace("{value}", binding.name())));
          }
        }
      }
    }
    found.sort(java.util.Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private static boolean candidate(final Binding binding, final Scope scope) {
    return VALUE_KINDS.contains(binding.kind())
        && scope.kind() != ScopeKind.FUNCTION
        && !IGNORED.equals(binding.name())
        && !IMPLICIT_IT.equals(binding.name());
  }

  private static boolean shadowsOuter(
      final Binding binding, final Scope scope, final UnitScopes scopes) {
    Scope current = scope;
    while (current.parent() != NO_SCOPE) {
      final Scope outer = scopes.scopes().get(current.parent());
      if (!LOCAL_SCOPES.contains(outer.kind())) {
        return false;
      }
      if (outer.bindings().stream()
          .anyMatch(
              other ->
                  declaredBefore(other, binding)
                      && other.name().equals(binding.name())
                      && VALUE_KINDS.contains(other.kind()))) {
        return true;
      }
      if (outer.kind() == ScopeKind.FUNCTION) {
        return false;
      }
      current = outer;
    }
    return false;
  }

  private static boolean declaredBefore(final Binding outer, final Binding inner) {
    return outer.declaration().startOffset() < inner.declaration().startOffset();
  }
}
