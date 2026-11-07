package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.Scope;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each node of the given kinds that no scope of the given kinds encloses, like pyflakes
 * {@code F704} and {@code F706}: a {@code yield} or {@code return} outside any function.
 */
public final class OutsideScopeCheck implements RuleCheck {

  private final Set<String> kinds;
  private final Set<ScopeKind> within;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kinds the node kinds to report
   * @param within the scope kinds that must enclose a node for it to be allowed
   * @param message the message of each finding
   */
  public OutsideScopeCheck(
      final Set<String> kinds, final Set<ScopeKind> within, final String message) {
    this.kinds = Set.copyOf(kinds);
    this.within = Set.copyOf(within);
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
    throw new AnalysisFailedException("the outside-scope check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final List<Span> enclosing =
        view.scopes().scopes().stream()
            .filter(scope -> within.contains(scope.kind()))
            .map(Scope::span)
            .toList();
    return SyntaxWalk.visits(root, kinds).stream()
        .map(visit -> visit.node().span())
        .filter(span -> enclosing.stream().noneMatch(outer -> contains(outer, span)))
        .map(span -> new RuleMatch(span, message))
        .toList();
  }

  private static boolean contains(final Span outer, final Span inner) {
    return outer.startOffset() <= inner.startOffset() && inner.endOffset() <= outer.endOffset();
  }
}
