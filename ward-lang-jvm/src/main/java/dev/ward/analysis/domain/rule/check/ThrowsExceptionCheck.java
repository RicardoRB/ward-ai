package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method or constructor whose {@code throws} clause names exactly {@code Exception}, as
 * PMD's {@code SignatureDeclareThrowsException} does ({@code Throwable} is not reported). Test
 * methods, JUnit lifecycle methods and methods annotated {@code @Override} are not reported.
 *
 * <p>shortcut: PMD's override test is type-resolved; an overriding method without {@code @Override}
 * is reported.
 */
public final class ThrowsExceptionCheck implements UnitCheck {
  private static final String LIST = "qualifiedNameList";
  private static final String MEMBER = "classBodyDeclaration";
  private static final Set<String> DECLARATIONS =
      Set.of(MemberDeclarations.METHOD, MemberDeclarations.CONSTRUCTOR);
  private static final Set<String> EXCEPTION = Set.of("Exception", "java.lang.Exception");
  private static final Set<String> IGNORED =
      Set.of(
          "Override",
          "Test",
          "RepeatedTest",
          "ParameterizedTest",
          "TestFactory",
          "TestTemplate",
          "BeforeEach",
          "AfterEach",
          "BeforeAll",
          "AfterAll",
          "Before",
          "After",
          "BeforeClass",
          "AfterClass");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the throws list
   */
  public ThrowsExceptionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, DECLARATIONS).stream()
        .filter(visit -> !ignored(visit.ancestors()))
        .flatMap(visit -> visit.node().children().stream())
        .filter(child -> LIST.equals(child.kind()))
        .filter(ThrowsExceptionCheck::namesException)
        .map(list -> CheckSupport.at(list.span(), message))
        .toList();
  }

  private static boolean ignored(final List<SyntaxNode> up) {
    for (int index = up.size() - 1; index >= 0; index--) {
      if (MEMBER.equals(up.get(index).kind())) {
        return !Collections.disjoint(JUnitMembers.annotations(up.get(index)), IGNORED);
      }
    }
    return false;
  }

  private static boolean namesException(final SyntaxNode list) {
    return list.children().stream().map(SyntaxWalk::text).anyMatch(EXCEPTION::contains);
  }
}
