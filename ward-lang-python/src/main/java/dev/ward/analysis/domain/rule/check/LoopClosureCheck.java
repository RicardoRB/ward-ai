package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Binding;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports a read inside a function or lambda of a loop variable the function does not bind itself,
 * like pyflakes-style {@code B023}: each call sees the variable's value when it runs, not when the
 * function was made.
 */
public final class LoopClosureCheck implements RuleCheck {

  private static final String NAME = "{value}";
  private static final Set<String> LOOPS = Set.of("for_stmt");
  private static final Set<ScopeKind> CLOSURES = Set.of(ScopeKind.FUNCTION, ScopeKind.LAMBDA);

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public LoopClosureCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the loop-closure check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes(Set.of());
    final List<Span> targets = loopTargets(root);
    final List<RuleMatch> found = new ArrayList<>();
    for (final NameReference reference : scopes.references()) {
      if (reference.resolved().isPresent()
          && isLoopTarget(reference.resolved().get(), targets)
          && isOwnScopeOf(scopes, reference, reference.resolved().get())) {
        found.add(new RuleMatch(reference.span(), message.replace(NAME, reference.name())));
      }
    }
    return found;
  }

  private static List<Span> loopTargets(final SyntaxNode root) {
    final List<Span> targets = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, LOOPS)) {
      targets.add(visit.node().children().get(1).span());
    }
    return targets;
  }

  private static boolean isLoopTarget(final Binding binding, final List<Span> targets) {
    return targets.stream()
        .anyMatch(
            target ->
                target.startOffset() <= binding.declaration().startOffset()
                    && binding.declaration().endOffset() <= target.endOffset());
  }

  /** Whether the read sits in a closure that is not the scope that binds the variable. */
  private static boolean isOwnScopeOf(
      final UnitScopes scopes, final NameReference reference, final Binding binding) {
    return reference.scope() != binding.scope() && closure(scopes, reference.scope());
  }

  private static boolean closure(final UnitScopes scopes, final int id) {
    return scopes.scopes().stream()
        .anyMatch(scope -> scope.id() == id && CLOSURES.contains(scope.kind()));
  }
}
