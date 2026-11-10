package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The overriding-thread-run check: a run method with no parameters in a Thread subclass. */
class OverridingThreadRunCheckTest extends TreeBuilders {

  private static final String RUN = "run";
  private static final String EXTENDS = "'extends'";

  private final OverridingThreadRunCheck check = new OverridingThreadRunCheck(MESSAGE);

  @Test
  void runWithoutParametersInAThreadSubclassIsReported() {
    assertThat(check.check(unit(classOf("Thread", runMethod(false))), FILE)).hasSize(1);
  }

  @Test
  void aQualifiedThreadSuperclassIsRecognized() {
    assertThat(check.check(unit(classOf("java.lang.Thread", runMethod(false))), FILE)).hasSize(1);
  }

  @Test
  void aRunMethodInAClassThatDoesNotExtendThreadIsNotReported() {
    assertThat(check.check(unit(classOf("Runnable", runMethod(false))), FILE)).isEmpty();
  }

  @Test
  void aClassWithoutSuperclassIsNotReported() {
    assertThat(check.check(unit(n("classDeclaration", id("Plain"), runMethod(false))), FILE))
        .isEmpty();
  }

  @Test
  void aRunMethodWithParametersIsNotReported() {
    assertThat(check.check(unit(classOf("Thread", runMethod(true))), FILE)).isEmpty();
  }

  @Test
  void otherMethodsInAThreadSubclassAreNotReported() {
    final SyntaxNode other = n(METHOD_DECLARATION, id("work"), n(FORMAL_PARAMETERS));
    assertThat(check.check(unit(classOf("Thread", other)), FILE)).isEmpty();
  }

  private static SyntaxNode classOf(final String superclass, final SyntaxNode member) {
    return n(
        "classDeclaration",
        id("Worker"),
        t(EXTENDS, "extends"),
        n("typeType", n(EXPRESSION, t(TOKEN_IDENTIFIER, superclass))),
        n(CLASS_BODY, n(CLASS_BODY_DECLARATION, n(MEMBER_DECLARATION, member))));
  }

  private static SyntaxNode runMethod(final boolean withParameter) {
    final SyntaxNode parameters =
        withParameter
            ? n(FORMAL_PARAMETERS, n(FORMAL_PARAMETER, t("int", "int"), id("x")))
            : n(FORMAL_PARAMETERS);
    return n(METHOD_DECLARATION, id(RUN), parameters, n(BLOCK));
  }
}
