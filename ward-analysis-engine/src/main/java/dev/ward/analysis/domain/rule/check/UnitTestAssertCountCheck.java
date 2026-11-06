package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports each JUnit test method with more assertion calls than {@code max}. */
public final class UnitTestAssertCountCheck implements UnitCheck {

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the most assertion calls a test method may make
   * @param message the message; {@code {value}} is the count and {@code {max}} the limit
   */
  public UnitTestAssertCountCheck(final int max, final String message) {
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("classBody")).stream()
        .flatMap(visit -> JUnitMembers.members(visit.node()).stream())
        .filter(JUnitMembers::isTest)
        .flatMap(member -> JUnitMembers.method(member).stream())
        .filter(method -> JUnitMembers.assertionCount(method) > max)
        .map(
            method ->
                CheckSupport.at(
                    method.span(),
                    CheckSupport.fill(
                        message,
                        String.valueOf(JUnitMembers.assertionCount(method)),
                        String.valueOf(max))))
        .toList();
  }
}
