package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method that looks like a test but is not annotated, in a class that has JUnit tests, as
 * PMD's DetachedTestCase does: a public or package-private, non-static, parameterless {@code void}
 * method with no annotation. The tests are read from the annotation names Test, ParameterizedTest,
 * RepeatedTest, TestFactory and TestTemplate.
 */
public final class DetachedTestCaseCheck implements UnitCheck {

  private static final Set<String> TESTS =
      Set.of("Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate");
  private static final String VOID = "void";
  private static final String FORMAL_PARAMETER = "formalParameter";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the method
   */
  public DetachedTestCaseCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(DetachedTestCaseCheck::hasTests)
        .flatMap(clazz -> ClassMembers.members(clazz).stream())
        .filter(DetachedTestCaseCheck::looksLikeTest)
        .map(member -> CheckSupport.at(ClassMembers.declarationOf(member).span(), message))
        .toList();
  }

  private static boolean hasTests(final SyntaxNode clazz) {
    return ClassMembers.members(clazz).stream()
        .anyMatch(member -> annotations(member).stream().anyMatch(DetachedTestCaseCheck::isTest));
  }

  private static boolean isTest(final String annotation) {
    final int arguments = annotation.indexOf('(');
    final String bare = arguments < 0 ? annotation : annotation.substring(0, arguments);
    return TESTS.contains(ClassMembers.simpleName(bare.substring(1)));
  }

  private static List<String> annotations(final SyntaxNode member) {
    return MemberDeclarations.modifiers(member).stream()
        .filter(modifier -> modifier.startsWith("@"))
        .toList();
  }

  private static boolean looksLikeTest(final SyntaxNode member) {
    final SyntaxNode declaration = ClassMembers.declarationOf(member);
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    return MemberDeclarations.METHOD.equals(declaration.kind())
        && modifiers.stream().noneMatch(DetachedTestCaseCheck::excludes)
        && VOID.equals(ClassMembers.typeText(declaration).orElse(""))
        && SyntaxWalk.descendants(declaration).stream()
            .noneMatch(node -> FORMAL_PARAMETER.equals(node.kind()));
  }

  private static boolean excludes(final String modifier) {
    return modifier.startsWith("@")
        || MemberDeclarations.PRIVATE.equals(modifier)
        || MemberDeclarations.PROTECTED.equals(modifier)
        || MemberDeclarations.STATIC.equals(modifier);
  }
}
