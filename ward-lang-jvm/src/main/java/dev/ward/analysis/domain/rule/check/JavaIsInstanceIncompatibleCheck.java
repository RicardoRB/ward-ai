package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports {@code Class<T>.isInstance(x)} when {@code x} has a final class type that is neither
 * {@code T} nor a subtype of it, so the call is always false, as Error Prone's
 * IsInstanceIncompatibleType does for the case that the facts can decide. The type argument must be
 * a qualified class name, and every fact that the decision reads must be known.
 */
public final class JavaIsInstanceIncompatibleCheck implements RuleCheck {

  private static final String CLASS = "java.lang.Class";
  private static final String METHOD = "isInstance";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the type of the argument and {@code {max}} the
   *     type argument of the class
   */
  public JavaIsInstanceIncompatibleCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return JavaCalls.WITH_MEMBERS;
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
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expression"))) {
      incompatible(visit.node(), types)
          .ifPresent(
              pair ->
                  matches.add(
                      CheckSupport.at(
                          visit.node().span(), CheckSupport.fill(message, pair[0], pair[1]))));
    }
    return List.copyOf(matches);
  }

  private static Optional<String[]> incompatible(final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span());
    final List<SyntaxNode> arguments = JavaCalls.arguments(call);
    if (target.isEmpty()
        || !CLASS.equals(target.get().declaringType().name())
        || !METHOD.equals(target.get().name())
        || arguments.size() != 1) {
      return Optional.empty();
    }
    final Optional<String> expected = typeArgument(call, types);
    final Optional<TypeRef> actual = types.typeOf(arguments.get(0).span());
    if (expected.isEmpty() || actual.isEmpty() || actual.get().arrayDepth() > 0) {
      return Optional.empty();
    }
    return finalWithout(arguments.get(0), actual.get().name(), expected.get(), types)
        ? Optional.of(new String[] {actual.get().name(), expected.get()})
        : Optional.empty();
  }

  private static Optional<String> typeArgument(final SyntaxNode call, final UnitTypeFacts types) {
    return JavaCalls.receiver(call)
        .flatMap(receiver -> types.typeOf(receiver.span()))
        .filter(type -> type.arguments().size() == 1)
        .map(type -> type.arguments().get(0))
        .filter(argument -> argument.arrayDepth() == 0 && argument.name().contains("."))
        .map(TypeRef::name);
  }

  private static boolean finalWithout(
      final SyntaxNode argument,
      final String actual,
      final String expected,
      final UnitTypeFacts types) {
    final boolean isFinalClass =
        types
            .shapeOf(argument.span())
            .filter(shape -> shape.kind() == TypeShape.Kind.CLASS && shape.isFinal())
            .isPresent();
    final Optional<TypeMembers> members = types.membersOf(argument.span());
    return isFinalClass
        && members.isPresent()
        && !actual.equals(expected)
        && members.get().ancestors().stream().noneMatch(type -> expected.equals(type.name()));
  }
}
