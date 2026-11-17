package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.scope.NameReference;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reports each reference to a name that matches a pattern and that no scope of the file declares,
 * so that a parameter or a variable of the same name is not the global it shadows.
 */
public final class UnresolvedNameCheck implements RuleCheck {
  private final String message;
  private final Pattern names;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   * @param names the names to report when they are not declared in the file
   */
  public UnresolvedNameCheck(final String message, final Pattern names) {
    this.message = message;
    this.names = names;
  }

  /**
   * Always fails: the check reads the scopes of the unit.
   *
   * @throws AnalysisFailedException always
   */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    throw new AnalysisFailedException("the unresolved-name check needs the scopes");
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView view,
      final SourceUnit unit
  ) {
    return view.scopes().unresolved().stream()
        .filter(reference -> names.matcher(reference.name()).matches())
        .map(this::match)
        .toList();
  }

  private RuleMatch match(final NameReference reference) {
    return new RuleMatch(reference.span(), message);
  }
}
