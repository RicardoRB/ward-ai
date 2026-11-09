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
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code suspend} function whose body has no suspension point: every call in it resolves
 * to a function without a {@code Continuation} parameter. A body with a call that is unresolved, or
 * that resolves to a suspend function, is never reported, because the call may suspend.
 */
public final class KotlinRedundantSuspendCheck implements RuleCheck {
  private static final String FUNCTION = "functionDeclaration";
  private static final String BODY = "functionBody";
  private static final String MODIFIER = "functionModifier";
  private static final String SUSPEND = "suspend";
  private static final String POSTFIX = "postfixUnaryExpression";
  private static final String OPERATION = "postfixUnaryOperation";
  private static final String CALL_SUFFIX = "callSuffix";
  private static final String CONTINUATION = "kotlin.coroutines.Continuation";
  private static final FactNeed NEED = new FactNeed(Set.of(POSTFIX), Set.of(FactKind.CALL));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the {@code suspend} modifier
   */
  public KotlinRedundantSuspendCheck(final String message) {
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
    return SyntaxWalk.visits(root, Set.of(FUNCTION)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(function -> redundant(function, types).stream())
        .map(modifier -> CheckSupport.at(modifier.span(), message))
        .toList();
  }

  /** Returns the {@code suspend} modifier of {@code function} when its body cannot suspend. */
  private static Optional<SyntaxNode> redundant(
      final SyntaxNode function, final UnitTypeFacts types) {
    final Optional<SyntaxNode> body =
        function.children().stream().filter(child -> BODY.equals(child.kind())).findFirst();
    return suspendModifier(function)
        .filter(modifier -> body.isPresent() && !maySuspend(body.get(), types));
  }

  private static Optional<SyntaxNode> suspendModifier(final SyntaxNode function) {
    return SyntaxWalk.visits(function, Set.of(MODIFIER)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(modifier -> SUSPEND.equals(SyntaxWalk.text(modifier)))
        .findFirst();
  }

  private static boolean maySuspend(final SyntaxNode body, final UnitTypeFacts types) {
    return SyntaxWalk.visits(body, Set.of(POSTFIX)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(KotlinRedundantSuspendCheck::isCall)
        .anyMatch(call -> mayBeSuspending(call, types));
  }

  private static boolean isCall(final SyntaxNode postfix) {
    return postfix.children().stream()
        .filter(child -> OPERATION.equals(child.kind()))
        .anyMatch(
            child -> child.children().stream().anyMatch(part -> CALL_SUFFIX.equals(part.kind())));
  }

  private static boolean mayBeSuspending(final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span());
    return target.isEmpty()
        || target.get().parameters().stream().anyMatch(KotlinRedundantSuspendCheck::isContinuation);
  }

  private static boolean isContinuation(final TypeRef type) {
    return CONTINUATION.equals(type.name());
  }
}
