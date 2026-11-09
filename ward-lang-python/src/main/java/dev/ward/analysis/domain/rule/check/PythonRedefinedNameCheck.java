package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.BindingFlag;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reports a local variable or parameter of a function that hides a module-level name, like pylint's
 * {@code redefined-outer-name}. A {@code global} or {@code nonlocal} declaration refers to the
 * outer name on purpose and is left alone. Shortcut: only the module scope counts as the outer
 * scope; names of enclosing functions are not compared.
 */
public final class PythonRedefinedNameCheck implements RuleCheck {

  private static final String NAME = "{value}";
  private static final Set<BindingKind> LOCAL_KINDS =
      Set.of(BindingKind.VARIABLE, BindingKind.PARAMETER, BindingKind.CONSTANT);
  private static final Set<BindingFlag> EXEMPT_FLAGS =
      Set.of(BindingFlag.GLOBAL, BindingFlag.NONLOCAL);

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public PythonRedefinedNameCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /**
   * Always fails: the check reads the scopes of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the redefined-outer-name check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final Set<String> outer =
        scopes.module().bindings().stream()
            .map(Binding::name)
            .collect(Collectors.toUnmodifiableSet());
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      if (scope.kind() == ScopeKind.FUNCTION) {
        scope.bindings().stream()
            .filter(binding -> hides(binding, outer))
            .forEach(binding -> found.add(match(binding)));
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private static boolean hides(final Binding binding, final Set<String> outer) {
    return LOCAL_KINDS.contains(binding.kind())
        && outer.contains(binding.name())
        && binding.flags().stream().noneMatch(EXEMPT_FLAGS::contains);
  }

  private RuleMatch match(final Binding binding) {
    return new RuleMatch(binding.declaration(), message.replace(NAME, binding.name()));
  }
}
