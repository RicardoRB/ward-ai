package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a lifecycle method, such as {@code tearDown()}, of a class with test methods when it has
 * none of the lifecycle annotations that the rule names.
 */
public final class UnitTestLifecycleCheck implements UnitCheck {

  private final String methodName;
  private final Set<String> annotations;
  private final String message;

  /**
   * Creates the check.
   *
   * @param methodName the lifecycle method's name
   * @param annotations the annotations that mark the method, by simple name
   * @param message the message; {@code {value}} is the method name
   */
  public UnitTestLifecycleCheck(
      final String methodName, final Set<String> annotations, final String message) {
    this.methodName = Objects.requireNonNull(methodName, "methodName");
    this.annotations = Set.copyOf(annotations);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("classBody")).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(body -> JUnitMembers.members(body).stream().anyMatch(JUnitMembers::isTest))
        .flatMap(body -> JUnitMembers.members(body).stream())
        .filter(
            member -> JUnitMembers.annotations(member).stream().noneMatch(annotations::contains))
        .flatMap(member -> JUnitMembers.method(member).stream())
        .filter(method -> methodName.equals(JUnitMembers.name(method)))
        .filter(JUnitMembers::hasNoParameters)
        .map(
            method ->
                CheckSupport.at(
                    method.span(), CheckSupport.fill(message, JUnitMembers.name(method), "")))
        .toList();
  }
}
