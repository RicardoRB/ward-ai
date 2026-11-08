package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.FieldRef;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports {@code new T(...)} where {@code T} is a plain class with only static methods and static
 * fields, so that the instance carries nothing. It reports nothing when the members or the shape of
 * the class are unknown.
 */
public final class StaticClassInstantiationCheck extends FactsCheck {

  private static final String EXPRESSION = "expression";
  private static final String CREATOR = "creator";
  private static final String OBJECT = "java.lang.Object";
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.MEMBERS, FactKind.SHAPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the {@code new} expression
   */
  public StaticClassInstantiationCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(EXPRESSION))) {
      final SyntaxNode node = visit.node();
      if (node.children().stream().anyMatch(child -> CREATOR.equals(child.kind()))
          && staticOnly(node, types)) {
        matches.add(CheckSupport.at(node.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean staticOnly(final SyntaxNode creation, final UnitTypeFacts types) {
    final boolean plain =
        types
            .shapeOf(creation.span())
            .filter(shape -> shape.kind() == TypeShape.Kind.CLASS && !shape.isAbstract())
            .isPresent();
    return plain
        && types
            .membersOf(creation.span())
            .filter(StaticClassInstantiationCheck::onlyStatic)
            .isPresent();
  }

  private static boolean onlyStatic(final TypeMembers members) {
    final List<CallTarget> methods =
        members.methods().stream().filter(method -> !method.name().startsWith("<")).toList();
    return !methods.isEmpty()
        && methods.stream().allMatch(CallTarget::isStatic)
        && members.fields().stream().allMatch(FieldRef::isStatic)
        && members.ancestors().stream().allMatch(ancestor -> OBJECT.equals(ancestor.name()));
  }
}
