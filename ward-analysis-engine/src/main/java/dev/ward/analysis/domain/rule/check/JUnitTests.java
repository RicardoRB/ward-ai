package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the JUnit parts of a class body: its members, their names, their annotations and their
 * modifiers. Annotations are matched by simple name, so {@code @org.junit.Test} is {@code Test}.
 */
final class JUnitTests {

  /** The annotations that mark a JUnit 4 or 5 test method. */
  static final Set<String> TEST_ANNOTATIONS =
      Set.of("Test", "RepeatedTest", "ParameterizedTest", "TestFactory", "TestTemplate");

  private static final String CLASS_BODY_DECLARATION = "classBodyDeclaration";
  private static final String MEMBER_DECLARATION = "memberDeclaration";
  private static final String METHOD_DECLARATION = "methodDeclaration";
  private static final String MODIFIER = "modifier";
  private static final String ANNOTATION = "annotation";
  private static final String IDENTIFIER = "identifier";
  private static final String METHOD_CALL = "methodCall";

  private JUnitTests() {}

  /** Returns the members of a class body, the {@code classBodyDeclaration} children. */
  static List<SyntaxNode> members(final SyntaxNode classBody) {
    return classBody.children().stream()
        .filter(child -> CLASS_BODY_DECLARATION.equals(child.kind()))
        .toList();
  }

  /** Returns the method of a member, when the member declares a method. */
  static Optional<SyntaxNode> method(final SyntaxNode member) {
    return member.children().stream()
        .filter(child -> MEMBER_DECLARATION.equals(child.kind()))
        .flatMap(memberDeclaration -> memberDeclaration.children().stream())
        .filter(child -> METHOD_DECLARATION.equals(child.kind()))
        .findFirst();
  }

  /** Returns the name of a method declaration. */
  static String name(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  /** Returns the simple names of the annotations on a member, without {@code @} or arguments. */
  static Set<String> annotations(final SyntaxNode member) {
    return member.children().stream()
        .filter(child -> MODIFIER.equals(child.kind()))
        .flatMap(modifier -> SyntaxWalk.descendants(modifier).stream())
        .filter(node -> ANNOTATION.equals(node.kind()))
        .map(node -> simpleName(SyntaxWalk.text(node)))
        .collect(Collectors.toUnmodifiableSet());
  }

  /** Returns whether a member has the modifier {@code public}. */
  static boolean isPublic(final SyntaxNode member) {
    return member.children().stream()
        .anyMatch(
            child -> MODIFIER.equals(child.kind()) && "public".equals(SyntaxWalk.text(child)));
  }

  /** Returns whether a member has the modifier {@code private}. */
  static boolean isPrivate(final SyntaxNode member) {
    return member.children().stream()
        .anyMatch(
            child -> MODIFIER.equals(child.kind()) && "private".equals(SyntaxWalk.text(child)));
  }

  /** Returns whether a member is a test method: it has one of the test annotations. */
  static boolean isTest(final SyntaxNode member) {
    return annotations(member).stream().anyMatch(TEST_ANNOTATIONS::contains);
  }

  /** Returns how many assertion calls a method body has: calls whose name starts with assert. */
  static long assertionCount(final SyntaxNode method) {
    return SyntaxWalk.descendants(method).stream()
        .filter(node -> METHOD_CALL.equals(node.kind()))
        .filter(
            node ->
                node.children().stream()
                    .anyMatch(
                        c ->
                            IDENTIFIER.equals(c.kind()) && SyntaxWalk.text(c).startsWith("assert")))
        .count();
  }

  /** Returns whether a method has no formal parameters. */
  static boolean hasNoParameters(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> "formalParameters".equals(child.kind()))
        .allMatch(
            child -> child.children().stream().noneMatch(c -> "formalParameter".equals(c.kind())));
  }

  /** Returns the class body of a class declaration, when it has one. */
  static Optional<SyntaxNode> body(final SyntaxNode classDeclaration) {
    return classDeclaration.children().stream()
        .filter(child -> "classBody".equals(child.kind()))
        .findFirst();
  }

  /** Returns the name of a class declaration. */
  static String className(final SyntaxNode classDeclaration) {
    return name(classDeclaration);
  }

  private static String simpleName(final String annotation) {
    final String withoutAt = annotation.startsWith("@") ? annotation.substring(1) : annotation;
    final int arguments = withoutAt.indexOf('(');
    final String name = arguments < 0 ? withoutAt : withoutAt.substring(0, arguments);
    return name.substring(name.lastIndexOf('.') + 1);
  }
}
