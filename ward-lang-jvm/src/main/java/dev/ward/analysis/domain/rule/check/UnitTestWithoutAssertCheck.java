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
        .filter(method -> JUnitMembers.assertionCount(method) == 0)
        .map(
            method ->
                CheckSupport.at(
                    method.span(), CheckSupport.fill(message, JUnitMembers.name(method), "")))
        .toList();
  }
}
