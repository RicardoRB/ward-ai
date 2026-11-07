package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The hidden-field check on small class bodies: hiding, static contexts and the two options. */
class HiddenFieldCheckTest extends TreeBuilders {

  private static final String COUNT = "count";
  private static final String MESSAGE = "hides {value}";
  private static final String STATIC = "'static'";
  private static final String VOID = "void";
  private static final String INT = "int";
  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String BLOCK_STATEMENT = "blockStatement";

  @Test
  void parameterOrLocalNamedAsAFieldIsReported() {
    final SyntaxNode body =
        n(CLASS_BODY, field(false, COUNT), method(false, VOID, "run", COUNT, false));
    assertThat(Trees.messages(new HiddenFieldCheck(false, false, MESSAGE).check(body, FILE)))
        .containsExactly("hides count");
    final SyntaxNode local = n(CLASS_BODY, field(false, COUNT), localMethod(false, COUNT));
    assertThat(new HiddenFieldCheck(false, false, MESSAGE).check(local, FILE)).hasSize(1);
  }

  @Test
  void staticContextHidesOnlyStaticFields() {
    final SyntaxNode instanceField = n(CLASS_BODY, field(false, COUNT), localMethod(true, COUNT));
    assertThat(new HiddenFieldCheck(false, false, MESSAGE).check(instanceField, FILE)).isEmpty();
    final SyntaxNode staticField = n(CLASS_BODY, field(true, COUNT), localMethod(true, COUNT));
    assertThat(new HiddenFieldCheck(false, false, MESSAGE).check(staticField, FILE)).hasSize(1);
  }

  @Test
  void setterParameterIsSkippedOnlyWhenTheOptionIsOn() {
    final SyntaxNode setter =
        n(CLASS_BODY, field(false, COUNT), method(false, VOID, "setCount", COUNT, false));
    assertThat(new HiddenFieldCheck(false, false, MESSAGE).check(setter, FILE)).hasSize(1);
    assertThat(new HiddenFieldCheck(false, true, MESSAGE).check(setter, FILE)).isEmpty();
    final SyntaxNode nonVoid =
        n(CLASS_BODY, field(false, COUNT), method(false, INT, "setCount", COUNT, false));
    assertThat(new HiddenFieldCheck(false, true, MESSAGE).check(nonVoid, FILE)).hasSize(1);
  }

  @Test
  void constructorParameterIsSkippedOnlyWhenTheOptionIsOn() {
    final SyntaxNode constructor =
        n(CLASS_BODY, field(false, COUNT), method(false, "", "Violating", COUNT, true));
    assertThat(new HiddenFieldCheck(false, false, MESSAGE).check(constructor, FILE)).hasSize(1);
    assertThat(new HiddenFieldCheck(true, false, MESSAGE).check(constructor, FILE)).isEmpty();
  }

  private static SyntaxNode member(final boolean isStatic, final SyntaxNode declaration) {
    final SyntaxNode[] children =
        isStatic
            ? new SyntaxNode[] {t(STATIC, "static"), n(MEMBER_DECLARATION, declaration)}
            : new SyntaxNode[] {n(MEMBER_DECLARATION, declaration)};
    return n(CLASS_BODY_DECLARATION, children);
  }

  private static SyntaxNode field(final boolean isStatic, final String name) {
    return member(
        isStatic,
        n(
            "fieldDeclaration",
            t(INT, INT),
            n("variableDeclarators", n("variableDeclarator", t(TOKEN_IDENTIFIER, name)))));
  }

  private static SyntaxNode method(
      final boolean isStatic,
      final String returnType,
      final String name,
      final String parameter,
      final boolean constructor) {
    final SyntaxNode head =
        constructor
            ? n(
                CONSTRUCTOR,
                t(TOKEN_IDENTIFIER, name),
                t(TOKEN_OPEN, "("),
                param(parameter),
                t(TOKEN_CLOSE, ")"))
            : n(
                METHOD_DECLARATION,
                t(
                    returnType.isEmpty() ? VOID : returnType,
                    returnType.isEmpty() ? VOID : returnType),
                t(TOKEN_IDENTIFIER, name),
                t(TOKEN_OPEN, "("),
                param(parameter),
                t(TOKEN_CLOSE, ")"));
    return member(isStatic, head);
  }

  private static SyntaxNode param(final String name) {
    return n(FORMAL_PARAMETER, t(INT, INT), n(PARAMETER_ID, t(TOKEN_IDENTIFIER, name)));
  }

  private static SyntaxNode localMethod(final boolean isStatic, final String name) {
    final SyntaxNode local =
        n(
            "localVariableDeclaration",
            t(INT, INT),
            n("variableDeclarator", t(TOKEN_IDENTIFIER, name)));
    return member(
        isStatic,
        n(
            METHOD_DECLARATION,
            t(VOID, VOID),
            t(TOKEN_IDENTIFIER, "run"),
            t(TOKEN_OPEN, "("),
            t(TOKEN_CLOSE, ")"),
            n(BLOCK, n(BLOCK_STATEMENT, local))));
  }
}
