package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reports a class or enum that declares {@code equals(Object)} without {@code hashCode()}, or the
 * other way round. Only the methods declared directly in the type's body count.
 */
public final class EqualsHashCodeCheck implements UnitCheck {

  private static final String EQUALS = "equals";
  private static final String HASH_CODE = "hashCode";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message at the method that is declared without its counterpart
   */
  public EqualsHashCodeCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TypeMethods.TYPES)) {
      final List<TypeMethods.Member> members = TypeMethods.of(visit.node());
      final Optional<TypeMethods.Member> equals = find(members, EQUALS, true);
      final Optional<TypeMethods.Member> hashCode = find(members, HASH_CODE, false);
      equals
          .filter(m -> hashCode.isEmpty())
          .ifPresent(m -> matches.add(CheckSupport.at(m.declaration().span(), message)));
      hashCode
          .filter(m -> equals.isEmpty())
          .ifPresent(m -> matches.add(CheckSupport.at(m.declaration().span(), message)));
    }
    return List.copyOf(matches);
  }

  private static Optional<TypeMethods.Member> find(
      final List<TypeMethods.Member> members, final String name, final boolean withObject) {
    return members.stream()
        .filter(m -> name.equals(TypeMethods.name(m.method())))
        .filter(m -> matchesParameters(m.method(), withObject))
        .findFirst();
  }

  private static boolean matchesParameters(final SyntaxNode method, final boolean withObject) {
    final List<String> types = TypeMethods.parameterTypes(method);
    return withObject ? types.size() == 1 && TypeMethods.isObject(types.get(0)) : types.isEmpty();
  }
}
