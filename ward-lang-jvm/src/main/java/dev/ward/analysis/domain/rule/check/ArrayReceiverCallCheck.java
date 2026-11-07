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
import java.util.Optional;
import java.util.Set;

/**
 * Reports a call of a named method with a given number of arguments on a receiver whose type is an
 * array, such as {@code values.hashCode()}: arrays inherit the identity methods of {@code Object}.
 * It reports nothing when the type of the receiver is unknown.
 */
public final class ArrayReceiverCallCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String IDENTIFIER = "identifier";
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final String method;
  private final int arguments;
  private final String message;

  /**
   * Creates the check.
   *
   * @param method the name of the method called on the array
   * @param arguments how many arguments the call passes
   * @param message the message reported at the call
   */
  public ArrayReceiverCallCheck(final String method, final int arguments, final String message) {
    this.method = Objects.requireNonNull(method, "method");
    this.arguments = arguments;
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
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(call -> callsOnArray(call, types))
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private boolean callsOnArray(final SyntaxNode call, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = call.children();
    final Optional<SyntaxNode> invoked =
        parts.stream().filter(part -> METHOD_CALL.equals(part.kind())).findFirst();
    if (parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind()) || invoked.isEmpty()) {
      return false;
    }
    return named(invoked.get())
        && argumentCount(invoked.get()) == arguments
        && types.typeOf(parts.get(0).span()).filter(type -> type.arrayDepth() > 0).isPresent();
  }

  private boolean named(final SyntaxNode invoked) {
    return invoked.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .anyMatch(child -> method.equals(SyntaxWalk.text(child)));
  }

  private static long argumentCount(final SyntaxNode invoked) {
    return SyntaxWalk.descendants(invoked).stream()
        .filter(node -> "expressionList".equals(node.kind()))
        .findFirst()
        .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).count())
        .orElse(0L);
  }
}
