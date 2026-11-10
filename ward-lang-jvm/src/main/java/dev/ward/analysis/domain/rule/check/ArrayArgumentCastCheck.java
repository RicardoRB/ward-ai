package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeComponent;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports {@code collection.toArray(array)} where the element type of the array can never be cast
 * to the element type of the collection, so that the call fails with an {@code ArrayStoreException}
 * at run time. The collection must be a {@code java.util} type with one type argument, whose
 * argument is its element type. It reports nothing when a type, shape or supertype list is unknown.
 */
public final class ArrayArgumentCastCheck extends FactsCheck {

  private static final String EXPRESSION = "expression";
  private static final String COLLECTION = "java.util.Collection";
  private static final String UTIL = "java.util.";
  private static final String TO_ARRAY = "toArray";
  private static final int RECEIVER_ARGUMENTS = 1;
  private static final FactNeed NEED =
      new FactNeed(
          Set.of(EXPRESSION),
          Set.of(FactKind.CALL, FactKind.TYPE, FactKind.MEMBERS, FactKind.SHAPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public ArrayArgumentCastCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final SyntaxNode call = visit.node();
      if (isToArray(call, types) && impossible(call, types)) {
        matches.add(CheckSupport.at(call.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isToArray(final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span());
    return target
        .filter(t -> TO_ARRAY.equals(t.name()) && t.parameters().size() == 1)
        .filter(t -> t.parameters().get(0).arrayDepth() == 1 && !t.typeParameters().isEmpty())
        .isPresent();
  }

  private static boolean impossible(final SyntaxNode call, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = call.children();
    final Optional<SyntaxNode> argument = CallArgumentNodes.first(call);
    if (parts.isEmpty() || !EXPRESSION.equals(parts.get(0).kind()) || argument.isEmpty()) {
      return false;
    }
    final Optional<TypeComponent> element = elementOf(parts.get(0), types);
    final Optional<TypeComponent> component = onlyComponent(argument.get(), types);
    return element.isPresent()
        && component.isPresent()
        && !TypeCasts.castable(component.get(), element.get());
  }

  /** Returns the element type of a {@code java.util} collection with one type argument. */
  private static Optional<TypeComponent> elementOf(
      final SyntaxNode receiver, final UnitTypeFacts types) {
    final boolean util =
        types
            .typeOf(receiver.span())
            .filter(t -> t.name().startsWith(UTIL) && t.arrayDepth() == 0)
            .isPresent();
    final boolean collection =
        types
                .membersOf(receiver.span())
                .filter(m -> m.ancestors().stream().anyMatch(a -> COLLECTION.equals(a.name())))
                .isPresent()
            || types.typeOf(receiver.span()).filter(t -> COLLECTION.equals(t.name())).isPresent();
    return util && collection ? onlyComponent(receiver, types) : Optional.empty();
  }

  private static Optional<TypeComponent> onlyComponent(
      final SyntaxNode expression, final UnitTypeFacts types) {
    return types
        .membersOf(expression.span())
        .map(TypeMembers::components)
        .filter(components -> components.size() == RECEIVER_ARGUMENTS)
        .map(components -> components.get(0))
        .filter(TypeComponent::known);
  }
}
