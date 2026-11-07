package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** Runs the accessor checks over a tree with a private nested class and its outside uses. */
class AccessorChecksTest extends TreeBuilders {

  private static final String PRIVATE = "private";
  private static final String MESSAGE = "message {value}";
  private static final int EIGHT = 8;
  private static final int ELEVEN = 11;

  @Test
  void aCreationOfAPrivateConstructorOutsideItsClassIsReportedBeforeJava11() {
    final SyntaxNode outside = unit(outerWithUse(n("creator", id("Inner"))));
    assertThat(new AccessorClassGenerationCheck(EIGHT, MESSAGE).check(outside, SourceFile.UNKNOWN))
        .hasSize(1);
    assertThat(new AccessorClassGenerationCheck(ELEVEN, MESSAGE).check(outside, SourceFile.UNKNOWN))
        .isEmpty();
    assertThat(new AccessorClassGenerationCheck(0, MESSAGE).check(outside, SourceFile.UNKNOWN))
        .isEmpty();
  }

  @Test
  void aCallOfAPrivateMethodOutsideItsClassIsReportedBeforeJava11() {
    final SyntaxNode outside = unit(outerWithMethodUse(n("methodCall", id("value"))));
    assertThat(new AccessorMethodGenerationCheck(EIGHT, MESSAGE).check(outside, SourceFile.UNKNOWN))
        .hasSize(1);
    assertThat(
            new AccessorMethodGenerationCheck(ELEVEN, MESSAGE).check(outside, SourceFile.UNKNOWN))
        .isEmpty();
  }

  private static SyntaxNode outerWithUse(final SyntaxNode use) {
    return n(
        "classDeclaration",
        t("'class'", "class"),
        id("Outer"),
        n(CLASS_BODY, nestedWithPrivateConstructor(), member(use)));
  }

  private static SyntaxNode outerWithMethodUse(final SyntaxNode use) {
    return n(
        "classDeclaration",
        t("'class'", "class"),
        id("Outer"),
        n(CLASS_BODY, nestedWithPrivateMethod(), member(use)));
  }

  private static SyntaxNode nestedWithPrivateConstructor() {
    final SyntaxNode constructor =
        n(
            "classBodyDeclaration",
            n("modifier", t("'private'", PRIVATE)),
            n("memberDeclaration", n("constructorDeclaration", id("Inner"))));
    return nested(constructor);
  }

  private static SyntaxNode nestedWithPrivateMethod() {
    final SyntaxNode method =
        n(
            "classBodyDeclaration",
            n("modifier", t("'private'", PRIVATE)),
            n("memberDeclaration", n("methodDeclaration", t("'int'", "int"), id("value"))));
    return nested(method);
  }

  private static SyntaxNode nested(final SyntaxNode member) {
    return n(
        "classBodyDeclaration",
        n(
            "memberDeclaration",
            n("classDeclaration", t("'class'", "class"), id("Inner"), n(CLASS_BODY, member))));
  }

  private static SyntaxNode member(final SyntaxNode use) {
    return n(
        "classBodyDeclaration",
        n(
            "memberDeclaration",
            n("methodDeclaration", t("'void'", "void"), id("run"), n("methodBody", use))));
  }
}
