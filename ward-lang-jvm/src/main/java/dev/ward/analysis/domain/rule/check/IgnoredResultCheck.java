package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an expression statement that calls a method of one of the given types and drops its
 * non-void result. These types are immutable, so the call has no other effect. It reports nothing
 * when the call is unknown.
 */
public final class IgnoredResultCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String STATEMENT = "statement";
  private static final String VOID = "void";
  private static final int STATEMENT_PARTS = 2;
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL));

  private final Set<String> declaringTypes;
  private final String message;

  /**
   * Creates the check.
   *
   * @param declaringTypes the qualified names of the types whose results must be used
   * @param message the message reported at the statement
   */
  public IgnoredResultCheck(final Set<String> declaringTypes, final String message) {
    this.declaringTypes = Set.copyOf(declaringTypes);
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
      final UnitTypeFacts facts,
      final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(statement -> dropsResult(statement, facts))
        .map(statement -> CheckSupport.at(statement.span(), message))
        .toList();
  }

  private boolean dropsResult(final SyntaxNode statement, final UnitTypeFacts facts) {
    final List<SyntaxNode> parts = statement.children();
    if (parts.size() != STATEMENT_PARTS || !EXPRESSION.equals(parts.get(0).kind())) {
      return false;
    }
    return facts
        .callTarget(parts.get(0).span())
        .filter(call -> declaringTypes.contains(call.declaringType().name()))
        .map(CallTarget::returnType)
        .filter(returned -> !VOID.equals(returned.name()))
        .isPresent();
  }
}
