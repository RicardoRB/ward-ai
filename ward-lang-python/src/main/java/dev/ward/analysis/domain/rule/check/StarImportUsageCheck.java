package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.NameReference;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports each name that no scope declares inside a scope a star import leaves open, since the
 * import may define it, like pyflakes {@code F405}.
 */
public final class StarImportUsageCheck implements RuleCheck {

  private static final String NAME = "{value}";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} stands for the name
   */
  public StarImportUsageCheck(final String message) {
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
    throw new AnalysisFailedException("the star-import check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final ScopeView view, final SourceUnit unit) {
    final UnitScopes scopes = view.scopes(Set.of());
    final List<RuleMatch> found = new ArrayList<>();
    for (final NameReference reference : scopes.unresolved()) {
      if (scopes.isOpen(reference)) {
        found.add(new RuleMatch(reference.span(), message.replace(NAME, reference.name())));
      }
    }
    return found;
  }
}
