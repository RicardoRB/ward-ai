package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class ErrorProneConstructorsTest extends ErrorProneTrees {

  private SyntaxNode assignment(final String target) {
    return node(
        EXPRESSION,
        LINE,
        COLUMN,
        leaf(TOKEN_IDENTIFIER, target, LINE, COLUMN),
        leaf("'='", "=", LINE, COLUMN),
        leaf("'1'", "1", LINE, COLUMN));
  }

  @Test
  void reportsConstructorAssignmentToNonFinalStatic() {
    final SyntaxNode clazz =
        classNamed(
            CLASS_NAME,
            member(field("count"), STATIC),
            member(constructorOf(assignment("count"), assignment("Sample.count"))));

    assertThat(run(new AssignmentToNonFinalStaticCheck(MESSAGE), clazz)).hasSize(2);
  }

  @Test
  void skipsFinalInstanceForeignAndNonConstructorAssignments() {
    final SyntaxNode clazz =
        classNamed(
            CLASS_NAME,
            member(field("limit"), STATIC, "final"),
            member(field("count"), STATIC),
            member(field("value")),
            member(
                constructorOf(
                    assignment("limit"),
                    assignment("value"),
                    assignment("Other.count"),
                    assignment("unknown"),
                    node(EXPRESSION, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, "count", LINE, COLUMN)))),
            member(method("reset", 0, 0, assignment("count"))));

    assertThat(run(new AssignmentToNonFinalStaticCheck(MESSAGE), clazz)).isEmpty();
  }

  private SyntaxNode call(final String name) {
    return node(METHOD_CALL, LINE, COLUMN, ident(name));
  }

  private SyntaxNode bareCall(final String name) {
    return node(EXPRESSION, LINE, COLUMN, call(name));
  }

  private SyntaxNode thisCall(final String name) {
    return node(
        EXPRESSION,
        LINE,
        COLUMN,
        node(EXPRESSION, LINE, COLUMN, leaf("'this'", "this", LINE, COLUMN)),
        leaf(DOT_KIND, DOT, LINE, COLUMN),
        call(name));
  }

  @Test
  void reportsOverridableCallsOnThisFromAConstructor() {
    final SyntaxNode clazz =
        classNamed(
            CLASS_NAME,
            member(method(START, 0, 0)),
            member(constructorOf(bareCall(START), thisCall(START))));

    assertThat(run(new ConstructorCallsOverridableMethodCheck(MESSAGE), clazz)).hasSize(2);
  }

  @Test
  void skipsSafeMethodsFinalClassesForeignReceiversAndNestedCode() {
    final SyntaxNode foreign =
        node(
            EXPRESSION,
            LINE,
            COLUMN,
            node(EXPRESSION, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, "other", LINE, COLUMN)),
            leaf(DOT_KIND, DOT, LINE, COLUMN),
            call(START));
    final SyntaxNode lambda = node("lambdaExpression", LINE, COLUMN, bareCall(START));
    final SyntaxNode clazz =
        classNamed(
            CLASS_NAME,
            member(method(START, 0, 0)),
            member(method("hidden", 0, 0), PRIVATE),
            member(method("shared", 0, 0), STATIC),
            member(method("sealed", 0, 0), "final"),
            member(
                constructorOf(
                    bareCall("hidden"),
                    bareCall("shared"),
                    bareCall("sealed"),
                    bareCall("unknown"),
                    foreign,
                    lambda)));

    assertThat(run(new ConstructorCallsOverridableMethodCheck(MESSAGE), clazz)).isEmpty();
    assertThat(
            run(
                new ConstructorCallsOverridableMethodCheck(MESSAGE),
                node(
                    "typeDeclaration",
                    1,
                    1,
                    node("classOrInterfaceModifier", 1, 1, leaf("'final'", "final", 1, 1)),
                    classNamed(
                        CLASS_NAME,
                        member(method(START, 0, 0)),
                        member(constructorOf(bareCall(START)))))))
        .isEmpty();
  }

  @Test
  void reportsUnannotatedTestLookingMethodsOfATestClass() {
    final SyntaxNode clazz =
        classNamed(
            CLASS_NAME,
            annotatedMember("@org.junit.jupiter.api.Test", method("works", 0, 0)),
            annotatedMember("@RepeatedTest(3)", method("again", 0, 0)),
            member(method("forgotten", 0, 0)),
            member(method("alsoForgotten", 0, 0), PUBLIC));

    assertThat(run(new DetachedTestCaseCheck(MESSAGE), clazz)).hasSize(2);
  }

  @Test
  void skipsHelpersOtherSignaturesAndClassesWithoutTests() {
    final SyntaxNode helpers =
        classNamed(
            CLASS_NAME,
            annotatedMember("@Test", method("works", 0, 0)),
            member(method("hidden", 0, 0), PRIVATE),
            member(method("guarded", 0, 0), "protected"),
            member(method("shared", 0, 0), STATIC),
            member(method("withArgument", 1, 0)),
            member(typedMethod("result", INT)),
            annotatedMember("@Before", method("setUp", 0, 0)),
            member(field("count")));
    final SyntaxNode plain =
        classNamed(
            "Plain", annotatedMember("@Other", method("run", 0, 0)), member(method("go", 0, 0)));

    assertThat(run(new DetachedTestCaseCheck(MESSAGE), helpers, plain)).isEmpty();
  }
}
