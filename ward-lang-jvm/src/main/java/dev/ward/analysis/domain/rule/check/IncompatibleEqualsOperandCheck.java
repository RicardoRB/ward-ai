package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
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
 * Reports {@code o instanceof T} in {@code equals(Object o)} where {@code T} is a class that is
 * neither a supertype nor a subtype of the class that declares the method. It reports nothing when
 * a type, shape or ancestor list is unknown, or when {@code T} is an interface that a subclass
 * could implement.
 */
public final class IncompatibleEqualsOperandCheck extends FactsCheck {

  private static final String CLASS = "classDeclaration";
  private static final String EXPRESSION = "expression";
  private static final String TYPE_TYPE = "typeType";
  private static final String EQUALS = "equals";
  private static final String INSTANCEOF = "'instanceof'";
  private static final int INSTANCEOF_PARTS = 3;
  private static final FactNeed NEED =
      new FactNeed(
          Set.of(CLASS, EXPRESSION, TYPE_TYPE),
          Set.of(FactKind.TYPE, FactKind.SHAPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the instanceof test
   */
  public IncompatibleEqualsOperandCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS))) {
      final Optional<String> self = types.typeOf(visit.node().span()).map(TypeRef::name);
      final Optional<TypeMembers> members = types.membersOf(visit.node().span());
      if (self.isPresent() && members.isPresent()) {
        for (final TypeMethods.Member method : TypeMethods.of(visit.node())) {
          if (EQUALS.equals(TypeMethods.name(method.method()))) {
            tests(method.method(), self.get(), members.get(), types, matches);
          }
        }
      }
    }
    return List.copyOf(matches);
  }

  private void tests(
      final SyntaxNode method,
      final String self,
      final TypeMembers members,
      final UnitTypeFacts types,
      final List<RuleMatch> out) {
    for (final SyntaxNode test : SyntaxWalk.descendants(method)) {
      final List<SyntaxNode> parts = test.children();
      if (EXPRESSION.equals(test.kind())
          && parts.size() == INSTANCEOF_PARTS
          && INSTANCEOF.equals(parts.get(1).kind())
          && TYPE_TYPE.equals(parts.get(2).kind())
          && incompatible(parts.get(2), self, members, types)) {
        out.add(CheckSupport.at(test.span(), message));
      }
    }
  }

  private static boolean incompatible(
      final SyntaxNode tested,
      final String self,
      final TypeMembers members,
      final UnitTypeFacts types) {
    final Optional<String> name = types.typeOf(tested.span()).map(TypeRef::name);
    final boolean plainClass =
        types.shapeOf(tested.span()).filter(s -> s.kind() == TypeShape.Kind.CLASS).isPresent();
    final Optional<TypeMembers> testedMembers = types.membersOf(tested.span());
    return plainClass
        && name.isPresent()
        && testedMembers.isPresent()
        && !name.get().equals(self)
        && members.ancestors().stream().noneMatch(a -> a.name().equals(name.get()))
        && testedMembers.get().ancestors().stream().noneMatch(a -> a.name().equals(self));
  }
}
