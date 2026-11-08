package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports {@code map.computeIfAbsent(key, Type::new)} where {@code Type} has exactly one
 * no-argument constructor and a one-argument constructor, so that the reference may pick the
 * one-argument one and pass the key to it. A key that is a plain name is not reported, because
 * Error Prone excuses a key named like the constructor parameter and the parameter names are not
 * known here. It reports nothing when a type or its constructors are unknown.
 */
public final class ComputeIfAbsentReferenceCheck extends FactsCheck {

  private static final String EXPRESSION = "expression";
  private static final String TYPE_TYPE = "typeType";
  private static final String MAP = "java.util.Map";
  private static final String COMPUTE_IF_ABSENT = "computeIfAbsent";
  private static final String CONSTRUCTOR = "<init>";
  private static final int ARGUMENTS = 2;
  private static final int REFERENCE_PARTS = 3;
  private static final FactNeed NEED =
      new FactNeed(
          Set.of(EXPRESSION, TYPE_TYPE), Set.of(FactKind.CALL, FactKind.TYPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the constructor reference
   */
  public ComputeIfAbsentReferenceCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final SyntaxNode call = visit.node();
      if (isComputeIfAbsent(call, types)) {
        final List<SyntaxNode> arguments = CallArgumentNodes.all(call);
        if (arguments.size() == ARGUMENTS
            && !plainName(arguments.get(0))
            && ambiguous(arguments.get(1), types)) {
          matches.add(CheckSupport.at(arguments.get(1).span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isComputeIfAbsent(final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span());
    final List<SyntaxNode> parts = call.children();
    return target
            .filter(t -> COMPUTE_IF_ABSENT.equals(t.name()) && t.parameters().size() == ARGUMENTS)
            .isPresent()
        && !parts.isEmpty()
        && EXPRESSION.equals(parts.get(0).kind())
        && isMap(parts.get(0), types);
  }

  private static boolean isMap(final SyntaxNode receiver, final UnitTypeFacts types) {
    return types.typeOf(receiver.span()).filter(t -> MAP.equals(t.name())).isPresent()
        || types
            .membersOf(receiver.span())
            .filter(members -> members.ancestors().stream().anyMatch(a -> MAP.equals(a.name())))
            .isPresent();
  }

  private static boolean plainName(final SyntaxNode key) {
    final String text = SyntaxWalk.text(key);
    return !text.isEmpty() && text.chars().allMatch(Character::isJavaIdentifierPart);
  }

  /** Tells whether the argument is {@code Type::new} for a type with the two constructors. */
  private static boolean ambiguous(final SyntaxNode argument, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = argument.children();
    if (parts.size() != REFERENCE_PARTS
        || !TYPE_TYPE.equals(parts.get(0).kind())
        || !"'::'".equals(parts.get(1).kind())
        || !"'new'".equals(parts.get(2).kind())) {
      return false;
    }
    final Optional<TypeMembers> members = types.membersOf(parts.get(0).span());
    return members.isPresent()
        && constructors(members.get(), 0) == 1
        && constructors(members.get(), 1) > 0;
  }

  private static long constructors(final TypeMembers members, final int parameters) {
    return members.methods().stream()
        .filter(m -> CONSTRUCTOR.equals(m.name()) && m.parameters().size() == parameters)
        .count();
  }
}
