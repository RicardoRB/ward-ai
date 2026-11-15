package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports each JUnit test method whose body calls no assertion method. */
public final class UnitTestWithoutAssertCheck implements UnitCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the method name
   */
  public UnitTestWithoutAssertCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("classBody")).stream()
        .flatMap(visit -> JUnitMembers.members(visit.node()).stream())
        .filter(JUnitMembers::isTest)
        .flatMap(member -> JUnitMembers.method(member).stream())
        .filter(method -> !callsProbableAssertion(method))
        .map(
            method ->
                CheckSupport.at(
                    method.span(), CheckSupport.fill(message, JUnitMembers.name(method), "")))
        .toList();
  }

  /**
   * Returns whether a method calls what PMD takes for an assertion: a method whose name starts with
   * {@code assert}, {@code check} or {@code verify}, or is {@code fail}.
   */
  private static boolean callsProbableAssertion(final SyntaxNode method) {
    return SyntaxWalk.descendants(method).stream()
        .filter(node -> "methodCall".equals(node.kind()))
        .flatMap(node -> node.children().stream())
        .filter(c -> "identifier".equals(c.kind()))
        .map(SyntaxWalk::text)
        .anyMatch(
            name ->
                name.startsWith("assert")
                    || name.startsWith("check")
                    || name.startsWith("verify")
                    || "fail".equals(name));
  }
}
