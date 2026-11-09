package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reports a promise where a value is expected: an {@code if} condition, an object spread, or a
 * promise-returning function passed where a void return is expected.
 */
public final class MisusedPromiseCheck extends TypedCheck {

  private static final String IF = "ifStatement";
  private static final String PROPERTY = "propertyAssignment";
  private static final String SPREAD = "...";

  private final boolean checksConditionals;
  private final boolean checksSpreads;
  private final boolean checksVoidReturn;
  private final String message;

  /** Creates the check with the rule's options and message. */
  public MisusedPromiseCheck(
      final boolean checksConditionals,
      final boolean checksSpreads,
      final boolean checksVoidReturn,
      final String message) {
    super();
    this.checksConditionals = checksConditionals;
    this.checksSpreads = checksSpreads;
    this.checksVoidReturn = checksVoidReturn;
    this.message = message;
  }

  @Override
  protected Set<FactKind> kinds() {
    return checksVoidReturn ? Set.of(FactKind.TYPE, FactKind.CALL) : super.kinds();
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    if (checksConditionals) {
      conditions(root, types, out);
    }
    if (checksSpreads) {
      spreads(root, types, out);
    }
    if (checksVoidReturn) {
      out.addAll(VoidPromises.find(root, types, message));
    }
    return out;
  }

  private void conditions(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> out) {
    for (final SyntaxNode statement : SyntaxText.nodesOf(root, IF)) {
      for (final SyntaxNode condition : SyntaxText.childrenOf(statement, SEQUENCE)) {
        if (isPromise(types, condition)) {
          out.add(new RuleMatch(condition.span(), message));
        }
      }
    }
  }

  private void spreads(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> out) {
    for (final SyntaxNode property : SyntaxText.nodesOf(root, PROPERTY)) {
      final SyntaxNode operand = SyntaxText.last(property);
      if (SyntaxText.hasToken(property, SPREAD)
          && EXPRESSION.equals(operand.kind())
          && isPromise(types, operand)) {
        out.add(new RuleMatch(operand.span(), message));
      }
    }
  }

  private static boolean isPromise(final UnitTypeFacts types, final SyntaxNode node) {
    return typeOf(types, node).filter(TypedCheck::isPromise).isPresent();
  }
}
