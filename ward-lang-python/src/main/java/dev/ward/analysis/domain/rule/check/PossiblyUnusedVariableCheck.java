package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
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
import java.util.Objects;
import java.util.Set;

/**
 * Reports a local variable that nothing reads, in a function that calls {@code locals()} after the
 * variable is bound, like pylint {@code possibly-unused-variable}: the call may read the variable.
 * A {@code locals()} call inside a nested function, lambda, class or comprehension reads that inner
 * scope, so it does not count for the enclosing function.
 */
public final class PossiblyUnusedVariableCheck implements RuleCheck {

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final String LOCALS_CALL = "locals()";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public PossiblyUnusedVariableCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /**
   * Always fails: the check reads the scopes of the unit, which only {@link #matches(SyntaxNode,
   * FlowView, ScopeView, SourceUnit)} is given.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the possibly-unused check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes();
    final List<Span> calls = localsCalls(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final Scope scope : scopes.scopes()) {
      if (UnusedBindings.isLocalScope(scope) && scope.open()) {
        found.addAll(reportedIn(scope, scopes, ownedCalls(calls, scopes, scope)));
      }
    }
    found.sort(Comparator.comparingInt((RuleMatch match) -> match.span().startOffset()));
    return found;
  }

  private List<RuleMatch> reportedIn(
      final Scope scope, final UnitScopes scopes, final List<Span> owned) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final Binding binding : scope.bindings()) {
      if (UnusedBindings.isUnused(binding, scopes) && readLater(binding, owned)) {
        found.add(new RuleMatch(binding.declaration(), message));
      }
    }
    return found;
  }

  /** True when a {@code locals()} call of the scope follows the binding on a later line. */
  private static boolean readLater(final Binding binding, final List<Span> owned) {
    final int bound = binding.declaration().startLine();
    return owned.stream().anyMatch(call -> call.startLine() > bound);
  }

  private static List<Span> localsCalls(final SyntaxNode root) {
    return SyntaxWalk.visits(root, CALLS).stream()
        .filter(visit -> SyntaxWalk.text(visit.node()).startsWith(LOCALS_CALL))
        .map(visit -> visit.node().span())
        .toList();
  }

  /** The calls whose innermost enclosing scope is {@code scope}. */
  private static List<Span> ownedCalls(
      final List<Span> calls, final UnitScopes scopes, final Scope scope) {
    return calls.stream().filter(call -> innermostId(scopes, call) == scope.id()).toList();
  }

  private static int innermostId(final UnitScopes scopes, final Span call) {
    return scopes.scopes().stream()
        .filter(candidate -> contains(candidate.span(), call))
        .max(
            Comparator.comparingInt((Scope candidate) -> candidate.span().startOffset())
                .thenComparingInt(Scope::id))
        .map(Scope::id)
        .orElse(Scope.NO_PARENT);
  }

  private static boolean contains(final Span region, final Span call) {
    return region.startOffset() <= call.startOffset() && call.startOffset() < region.endOffset();
  }
}
