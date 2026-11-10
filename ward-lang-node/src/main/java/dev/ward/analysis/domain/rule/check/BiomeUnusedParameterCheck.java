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
import dev.ward.analysis.domain.scope.ReferenceAccess;
import dev.ward.analysis.domain.scope.ScopeKind;
import dev.ward.analysis.domain.scope.UnitScopes;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Reports each function or arrow parameter that nothing reads. A name that starts with {@code _} is
 * left alone. A destructured parameter is reported only when {@code ignoreRestSiblings} is false,
 * since the rule cannot yet tell which destructured names sit next to a rest element. shortcut:
 * with ignoreRestSiblings true every destructured parameter is skipped, not only the siblings of a
 * rest element; upgrade when the pattern's rest element is visible to the check
 */
public final class BiomeUnusedParameterCheck implements RuleCheck {

  private static final Set<ScopeKind> FUNCTIONS = Set.of(ScopeKind.FUNCTION, ScopeKind.LAMBDA);
  private static final String IGNORED_PREFIX = "_";

  private final boolean ignoreRestSiblings;
  private final String message;

  /**
   * Creates the check.
   *
   * @param ignoreRestSiblings whether destructured parameters are skipped
   * @param message the message of each finding; {@code {value}} is the parameter's name
   */
  public BiomeUnusedParameterCheck(final boolean ignoreRestSiblings, final String message) {
    this.ignoreRestSiblings = ignoreRestSiblings;
    this.message = message;
  }

  /**
   * Always fails: the check reads the scopes, which only {@link #matches(SyntaxNode, FlowView,
   * ScopeView, SourceUnit)} is given.
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
    return scopes.scopes().stream()
        .filter(scope -> FUNCTIONS.contains(scope.kind()) && !scope.open())
        .flatMap(scope -> scope.bindings().stream())
        .filter(binding -> unused(binding, scopes))
        .map(
            binding ->
                CheckSupport.at(
                    binding.declaration(), CheckSupport.fill(message, binding.name(), "")))
        .sorted(Comparator.comparingInt(match -> match.span().startOffset()))
        .toList();
  }

  private boolean unused(final Binding binding, final UnitScopes scopes) {
    return binding.kind() == BindingKind.PARAMETER
        && !binding.name().startsWith(IGNORED_PREFIX)
        && !(ignoreRestSiblings && binding.has(BindingFlag.DESTRUCTURED))
        && scopes.usesOf(binding).stream().noneMatch(use -> use.access() != ReferenceAccess.WRITE);
  }
}
