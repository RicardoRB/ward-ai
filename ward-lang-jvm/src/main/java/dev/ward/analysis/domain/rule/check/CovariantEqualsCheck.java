package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reports a class, enum or record that declares {@code equals} with a parameter other than {@code
 * Object} and no {@code equals(Object)} of its own, because such a method does not override {@code
 * Object.equals}. Only the methods declared directly in the type's body count.
 */
public final class CovariantEqualsCheck implements UnitCheck {

  private static final String EQUALS = "equals";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the covariant {@code equals}
   */
  public CovariantEqualsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TypeMethods.TYPES)) {
      final List<TypeMethods.Member> equals =
          TypeMethods.of(visit.node()).stream()
              .filter(m -> EQUALS.equals(TypeMethods.name(m.method())))
              .filter(m -> TypeMethods.parameterTypes(m.method()).size() == 1)
              .toList();
      if (equals.stream().noneMatch(m -> overridesObject(m.method()))) {
        equals.forEach(
            m ->
                matches.add(
                    CheckSupport.at(
                        TypeMethods.identifier(m.method()).orElse(m.method()).span(), message)));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean overridesObject(final SyntaxNode method) {
    return TypeMethods.isObject(TypeMethods.parameterTypes(method).get(0));
  }
}
