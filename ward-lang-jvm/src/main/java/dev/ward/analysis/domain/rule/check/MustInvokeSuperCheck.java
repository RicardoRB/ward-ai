package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method that overrides a method annotated {@code @OverridingMethodsMustInvokeSuper} or
 * {@code @OverrideMustInvoke} and does not call {@code super.name(...)}. It reports nothing when
 * the overridden method is unknown.
 */
public final class MustInvokeSuperCheck implements RuleCheck {
  private static final String METHOD = "methodDeclaration";
  private static final Set<String> MARKERS =
      Set.of("OverridingMethodsMustInvokeSuper", "OverrideMustInvoke");
  private static final FactNeed NEED = new FactNeed(Set.of(METHOD), Set.of(FactKind.OVERRIDES));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the method
   */
  public MustInvokeSuperCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(METHOD)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(method -> overridesMarked(method, types) && !callsSuper(method))
        .map(method -> CheckSupport.at(method.span(), message))
        .toList();
  }

  private static boolean overridesMarked(final SyntaxNode method, final UnitTypeFacts types) {
    return types.at(method.span()).overrides().stream()
        .flatMap(target -> target.annotations().stream())
        .anyMatch(
            annotation -> MARKERS.contains(annotation.substring(annotation.lastIndexOf('.') + 1)));
  }

  private static boolean callsSuper(final SyntaxNode method) {
    return SyntaxWalk.text(method).contains("super." + JUnitMembers.name(method) + "(");
  }
}
