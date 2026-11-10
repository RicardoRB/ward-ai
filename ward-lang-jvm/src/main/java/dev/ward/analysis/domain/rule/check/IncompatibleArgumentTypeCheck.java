package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Reports an argument passed for a parameter marked {@code @CompatibleWith("T")}, where {@code T}
 * is a type parameter of the receiver's type, when the argument's type can never be that type, as
 * Error Prone's IncompatibleArgumentType does. It judges the same cases as UnrelatedArgumentCheck:
 * a final argument type that is neither the expected type nor a subtype of it, or a primitive whose
 * box is another type. Unknown facts report nothing.
 */
public final class IncompatibleArgumentTypeCheck extends FactsCheck {

  private static final String EXPRESSION = "expression";
  private static final FactNeed NEED =
      new FactNeed(
          Set.of(EXPRESSION),
          Set.of(FactKind.CALL, FactKind.TYPE, FactKind.SHAPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the argument; {@code {value}} is the expected type
   */
  public IncompatibleArgumentTypeCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(call -> reportsOf(call, types).stream())
        .toList();
  }

  private List<RuleMatch> reportsOf(final SyntaxNode call, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = call.children();
    final Optional<CallTarget> target = types.callTarget(call.span());
    if (target.isEmpty() || parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind())) {
      return List.of();
    }
    final Optional<TypeRef> receiver = types.typeOf(parts.get(0).span());
    final List<SyntaxNode> arguments = CallArgumentNodes.all(call);
    if (receiver.isEmpty() || arguments.size() != target.get().parameters().size()) {
      return List.of();
    }
    return IntStream.range(0, arguments.size())
        .mapToObj(
            index -> reportOf(index, arguments.get(index), target.get(), receiver.get(), types))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<RuleMatch> reportOf(
      final int index,
      final SyntaxNode argument,
      final CallTarget target,
      final TypeRef receiver,
      final UnitTypeFacts types) {
    if (index >= target.compatibleWith().size() || target.compatibleWith().get(index).isEmpty()) {
      return Optional.empty();
    }
    final int slot = target.declaringTypeParameters().indexOf(target.compatibleWith().get(index));
    if (slot < 0 || slot >= receiver.arguments().size()) {
      return Optional.empty();
    }
    final TypeRef expected = receiver.arguments().get(slot);
    return UnrelatedArgumentCheck.excludes(expected, argument, types)
        ? Optional.of(
            CheckSupport.at(argument.span(), CheckSupport.fill(message, expected.name(), "")))
        : Optional.empty();
  }
}
