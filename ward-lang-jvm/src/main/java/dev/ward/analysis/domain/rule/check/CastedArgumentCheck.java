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
 * Reports a call {@code Receiver.method((cast) value)} whose only argument is a cast of a value of
 * one of the given types, such as {@code Math.round((float) count)} with an {@code int} count. It
 * reports nothing when the type of the cast value is unknown.
 */
public final class CastedArgumentCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String TYPE_TYPE = "typeType";
  private static final int CAST_PARTS = 4;
  private static final int CAST_TYPE = 1;
  private static final int CAST_OPERAND = 3;
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));

  private final String receiver;
  private final String method;
  private final String cast;
  private final Set<String> operandTypes;
  private final String message;

  /**
   * Creates the check.
   *
   * @param receiver the source text of the receiver, such as {@code Math}
   * @param method the name of the method
   * @param cast the type the argument is cast to
   * @param operandTypes the type names of the cast value that are reported
   * @param message the message reported at the call
   */
  public CastedArgumentCheck(
      final String receiver,
      final String method,
      final String cast,
      final Set<String> operandTypes,
      final String message) {
    this.receiver = Objects.requireNonNull(receiver, "receiver");
    this.method = Objects.requireNonNull(method, "method");
    this.cast = Objects.requireNonNull(cast, "cast");
    this.operandTypes = Set.copyOf(operandTypes);
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
        .filter(call -> castsArgument(call, types))
        .map(call -> CheckSupport.at(call.span(), message))
        .toList();
  }

  private boolean castsArgument(final SyntaxNode call, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = call.children();
    final Optional<SyntaxNode> invoked =
        parts.stream().filter(part -> METHOD_CALL.equals(part.kind())).findFirst();
    if (parts.isEmpty() || invoked.isEmpty() || !receiver.equals(SyntaxWalk.text(parts.get(0)))) {
      return false;
    }
    final boolean named =
        invoked.get().children().stream()
            .anyMatch(c -> "identifier".equals(c.kind()) && method.equals(SyntaxWalk.text(c)));
    return named && onlyArgument(invoked.get()).filter(arg -> castOperand(arg, types)).isPresent();
  }

  private boolean castOperand(final SyntaxNode argument, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = argument.children();
    return parts.size() == CAST_PARTS
        && TYPE_TYPE.equals(parts.get(CAST_TYPE).kind())
        && cast.equals(SyntaxWalk.text(parts.get(CAST_TYPE)))
        && types
            .typeOf(parts.get(CAST_OPERAND).span())
            .filter(type -> type.arrayDepth() == 0 && operandTypes.contains(type.name()))
            .isPresent();
  }

  private static Optional<SyntaxNode> onlyArgument(final SyntaxNode invoked) {
    final List<SyntaxNode> arguments =
        SyntaxWalk.descendants(invoked).stream()
            .filter(node -> "expressionList".equals(node.kind()))
            .findFirst()
            .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).toList())
            .orElse(List.of());
    return arguments.size() == 1 ? Optional.of(arguments.get(0)) : Optional.empty();
  }
}
