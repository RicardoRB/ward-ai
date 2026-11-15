package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method without parameters whose whole name is {@code hashCode} in any letter case and
 * whose return type is not {@code int}: it does not override {@code Object.hashCode}. A longer name
 * that merely ends in "hashcode" is a different method.
 */
public final class SuspiciousHashCodeNameCheck implements RuleCheck {
  private static final String NAME = "hashcode";
  private static final String INT = "int";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the method declaration
   */
  public SuspiciousHashCodeNameCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.METHOD)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(method -> NAME.equalsIgnoreCase(MemberDeclarations.name(method)))
        .filter(method -> ClassMetrics.parameterCount(method) == 0)
        .filter(method -> !INT.equals(returnType(method)))
        .map(method -> CheckSupport.at(method.span(), message))
        .toList();
  }

  private static String returnType(final SyntaxNode method) {
    return method.children().isEmpty() ? "" : SyntaxWalk.text(method.children().get(0));
  }
}
