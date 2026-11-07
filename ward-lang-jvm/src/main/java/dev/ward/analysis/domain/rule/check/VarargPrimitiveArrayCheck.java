package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a call of a variable-arity method with an {@code Object...} parameter whose variable part
 * receives a primitive array: the array becomes one element instead of being spread. It reports
 * nothing when the call or an argument type is unknown.
 */
public final class VarargPrimitiveArrayCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String OBJECT = "java.lang.Object";
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public VarargPrimitiveArrayCheck(final String message) {
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
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(call -> spreadsPrimitiveArray(call, facts))
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private static boolean spreadsPrimitiveArray(final SyntaxNode call, final UnitTypeFacts facts) {
    final CallTarget target = facts.callTarget(call.span()).orElse(null);
    if (target == null || !target.isVarargs() || target.parameters().isEmpty()) {
      return false;
    }
    final TypeRef last = target.parameters().get(target.parameters().size() - 1);
    if (!OBJECT.equals(last.name()) || last.arrayDepth() == 0) {
      return false;
    }
    final List<SyntaxNode> arguments = arguments(call);
    for (int i = target.parameters().size() - 1; i < arguments.size(); i++) {
      if (primitiveArray(arguments.get(i), facts)) {
        return true;
      }
    }
    return false;
  }

  private static boolean primitiveArray(final SyntaxNode argument, final UnitTypeFacts facts) {
    return facts
        .typeOf(argument.span())
        .filter(t -> t.primitive() && t.arrayDepth() > 0)
        .isPresent();
  }

  private static List<SyntaxNode> arguments(final SyntaxNode call) {
    return SyntaxWalk.descendants(call).stream()
        .filter(node -> "methodCall".equals(node.kind()))
        .findFirst()
        .flatMap(
            invoked ->
                SyntaxWalk.descendants(invoked).stream()
                    .filter(node -> "expressionList".equals(node.kind()))
                    .findFirst())
        .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).toList())
        .orElse(List.of());
  }
}
