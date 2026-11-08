package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a class whose {@code equals} and {@code hashCode} do not fit the ones it inherits, read
 * from the declared methods and the inherited methods of the member facts. It reports nothing when
 * the member facts of the class are unknown, because it needs to know which ancestor declares each
 * method.
 */
public final class EqualsInheritanceCheck extends FactsCheck {

  /** What the check looks for. */
  public enum Mode {
    /**
     * A class with {@code equals(Self)} and no {@code equals(Object)} that extends a class with
     * one.
     */
    COVARIANT_EQUALS_OVER_INHERITED,
    /**
     * A class that adds fields to a superclass with {@code equals(Object)} and does not override
     * it.
     */
    FIELDS_WITHOUT_EQUALS,
    /** A class with {@code hashCode()} that inherits {@code equals(Object)} from {@code Object}. */
    HASH_CODE_WITH_OBJECT_EQUALS,
    /**
     * A class that inherits {@code equals(Object)} from an abstract class and {@code hashCode()}
     * from {@code Object}.
     */
    INHERITED_EQUALS_WITH_OBJECT_HASH_CODE
  }

  private static final String CLASS = "classDeclaration";
  private static final String TYPE_TYPE = "typeType";
  private static final String EQUALS = "equals";
  private static final String HASH_CODE = "hashCode";
  private static final FactNeed NEED =
      new FactNeed(Set.of(CLASS, TYPE_TYPE), Set.of(FactKind.MEMBERS, FactKind.SHAPE));

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what the check looks for
   * @param message the message reported at the method or class
   */
  public EqualsInheritanceCheck(final Mode mode, final String message) {
    super(NEED);
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS))) {
      types
          .membersOf(visit.node().span())
          .ifPresent(members -> judge(visit, members, types).ifPresent(matches::add));
    }
    return List.copyOf(matches);
  }

  /** What a class declares and what it inherits, for the four modes. */
  private record Subject(
      SyntaxNode type,
      List<SyntaxNode> ancestors,
      List<TypeMethods.Member> own,
      TypeMembers members) {

    boolean declaresEquals() {
      return EqualsMethods.find(own, true).isPresent();
    }

    boolean inheritsOtherEquals() {
      return EqualsMethods.inherits(members, EQUALS, 1, false);
    }
  }

  private Optional<RuleMatch> judge(
      final SyntaxWalk.Visit visit, final TypeMembers members, final UnitTypeFacts types) {
    final Subject subject =
        new Subject(visit.node(), visit.ancestors(), TypeMethods.of(visit.node()), members);
    final Optional<SyntaxNode> at =
        switch (mode) {
          case COVARIANT_EQUALS_OVER_INHERITED -> covariantOverInherited(subject);
          case FIELDS_WITHOUT_EQUALS -> fieldsWithoutEquals(subject);
          case HASH_CODE_WITH_OBJECT_EQUALS -> hashCodeWithObjectEquals(subject);
          default -> inheritedEqualsWithObjectHashCode(subject, types);
        };
    return at.map(node -> CheckSupport.at(node.span(), message));
  }

  private static Optional<SyntaxNode> covariantOverInherited(final Subject subject) {
    return EqualsMethods.covariant(subject.type(), subject.own(), subject.ancestors())
        .filter(m -> !subject.declaresEquals() && subject.inheritsOtherEquals());
  }

  private static Optional<SyntaxNode> fieldsWithoutEquals(final Subject subject) {
    return Optional.of(subject.type())
        .filter(
            t ->
                !subject.declaresEquals()
                    && subject.inheritsOtherEquals()
                    && EqualsMethods.hasInstanceField(t));
  }

  private static Optional<SyntaxNode> hashCodeWithObjectEquals(final Subject subject) {
    return EqualsMethods.declared(subject.own(), HASH_CODE, 0)
        .filter(
            h ->
                !subject.declaresEquals()
                    && EqualsMethods.inherits(subject.members(), EQUALS, 1, true)
                    && !subject.inheritsOtherEquals());
  }

  private static Optional<SyntaxNode> inheritedEqualsWithObjectHashCode(
      final Subject subject, final UnitTypeFacts types) {
    final TypeMembers members = subject.members();
    return Optional.of(subject.type())
        .filter(
            t ->
                !subject.declaresEquals()
                    && EqualsMethods.declared(subject.own(), HASH_CODE, 0).isEmpty()
                    && subject.inheritsOtherEquals()
                    && EqualsMethods.inherits(members, HASH_CODE, 0, true)
                    && !EqualsMethods.inherits(members, HASH_CODE, 0, false)
                    && EqualsMethods.abstractSuperclass(t, types));
  }
}
