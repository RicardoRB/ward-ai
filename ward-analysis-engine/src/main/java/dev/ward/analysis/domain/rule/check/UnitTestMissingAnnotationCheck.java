package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each public method named {@code test...} in a test class that has no test annotation. A
 * test class is one whose name matches the pattern, or one that extends {@code TestCase}.
 */
public final class UnitTestMissingAnnotationCheck implements UnitCheck {

  private static final String TEST_CASE = "TestCase";

  private final Pattern classPattern;
  private final String message;

  /**
   * Creates the check.
   *
   * @param classPattern the pattern a test class name matches
   * @param message the message; {@code {value}} is the method name
   */
  public UnitTestMissingAnnotationCheck(final Pattern classPattern, final String message) {
    this.classPattern = Objects.requireNonNull(classPattern, "classPattern");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("classDeclaration")).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(this::isTestClass)
        .flatMap(clazz -> JUnitMembers.body(clazz).stream())
        .flatMap(body -> JUnitMembers.members(body).stream())
        .filter(JUnitMembers::isPublic)
        .filter(member -> !JUnitMembers.isTest(member))
        .flatMap(member -> JUnitMembers.method(member).stream())
        .filter(method -> JUnitMembers.name(method).startsWith("test"))
        .map(
            method ->
                CheckSupport.at(
                    method.span(), CheckSupport.fill(message, JUnitMembers.name(method), "")))
        .toList();
  }

  private boolean isTestClass(final SyntaxNode clazz) {
    return classPattern.matcher(JUnitMembers.className(clazz)).find() || extendsTestCase(clazz);
  }

  private static boolean extendsTestCase(final SyntaxNode clazz) {
    return clazz.children().stream()
        .filter(header -> !"classBody".equals(header.kind()))
        .flatMap(header -> SyntaxWalk.descendants(header).stream())
        .anyMatch(
            node ->
                node.children().isEmpty()
                    && (TEST_CASE.equals(SyntaxWalk.text(node))
                        || SyntaxWalk.text(node).endsWith("." + TEST_CASE)));
  }
}
