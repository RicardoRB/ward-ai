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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reports each parameter of a function that the function's scope never reads, like detekt's {@code
 * UnusedParameter}. A function whose enclosing type is open may have its signature fixed by a
 * supertype, so its parameters are left alone, and so are names that start with {@code _}. A
 * language may also narrow the functions with a {@link FunctionFilter}.
 */
public final class UnusedParameterCheck implements RuleCheck {

  private static final String NAME = "{value}";
  private static final String IGNORED_PREFIX = "_";

  private final String message;
  private final FunctionFilter filter;

  /**
   * Which function scopes the check reads, given the scope and the unit it belongs to. A language
   * uses it to leave out declarations its own syntax fixes, such as overrides or attributed
   * functions.
   */
  @FunctionalInterface
  public interface FunctionFilter {

    /** Whether the parameters of {@code function} are checked. */
    boolean checks(Scope function, SourceUnit unit);
  }

  /**
   * Creates the check over every function.
   *
   * @param message the message of each finding; {@code {value}} stands for the parameter name
   */
  public UnusedParameterCheck(final String message) {
    this(message, (function, unit) -> true);
  }

  /**
   * Creates the check over the functions that {@code filter} accepts.
   *
   * @param message the message of each finding; {@code {value}} stands for the parameter name
   * @param filter which function scopes are checked
   */
  public UnusedParameterCheck(final String message, final FunctionFilter filter) {
    this.message = message;
    this.filter = filter;
  }

  /**
   * Always fails: the check reads the scopes of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unused-parameter check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      if (scope.kind() == ScopeKind.FUNCTION
          && !insideOpenType(scope, scopes)
          && filter.checks(scope, unit)) {
        scope.bindings().stream()
            .filter(binding -> unused(binding, scopes))
            .forEach(binding -> found.add(match(binding)));
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private RuleMatch match(final Binding binding) {
    return new RuleMatch(binding.declaration(), message.replace(NAME, binding.name()));
  }

  private static boolean unused(final Binding binding, final UnitScopes scopes) {
    return binding.kind() == BindingKind.PARAMETER
        && !binding.name().startsWith(IGNORED_PREFIX)
        && scopes.usesOf(binding).isEmpty();
  }

  private static boolean insideOpenType(final Scope scope, final UnitScopes scopes) {
    Optional<Scope> ancestor = scopes.parent(scope);
    while (ancestor.isPresent()) {
      final Scope current = ancestor.get();
      if (current.kind() == ScopeKind.CLASS && current.open()) {
        return true;
      }
      ancestor = scopes.parent(current);
    }
    return false;
  }
}
