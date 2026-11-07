package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The hidden-field check on small class bodies: hiding, static contexts and the two options. */
class HiddenFieldCheckTest extends TreeBuilders {

  private static final String MESSAGE = "hides {value}";
  private static final String STATIC = "'static'";
  private static final String VOID = "void";
  private static final String INT = "int";
  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String BLOCK_STATEMENT = "blockStatement";

  @Test
  void parameterOrLocalNamedAsAFieldIsReported() {
    final SyntaxNode body =
        classBody(field(false, "count"), method(false, VOID, "run", "count", false));
    assertThat(Trees.messages(run(new HiddenFieldCheck(false, false, MESSAGE), body)))
        .containsExactly("hides count");
    final SyntaxNode local = classBody(field(false, "count"), localMethod(false, "count"));
    assertThat(run(new HiddenFieldCheck(false, false, MESSAGE), local)).hasSize(1);
  }

  @Test
  void staticContextHidesOnlyStaticFields() {
    final SyntaxNode instanceField = classBody(field(false, "count"), localMethod(true, "count"));
    assertThat(run(new HiddenFieldCheck(false, false, MESSAGE), instanceField)).isEmpty();
    final SyntaxNode staticField = classBody(field(true, "count"), localMethod(true, "count"));
    assertThat(run(new HiddenFieldCheck(false, false, MESSAGE), staticField)).hasSize(1);
  }

  @Test
  void setterParameterIsSkippedOnlyWhenTheOptionIsOn() {
    final SyntaxNode setter =
        classBody(field(false, "count"), method(false, VOID, "setCount", "count", false));
    assertThat(run(new HiddenFieldCheck(false, false, MESSAGE), setter)).hasSize(1);
    assertThat(run(new HiddenFieldCheck(false, true, MESSAGE), setter)).isEmpty();
    final SyntaxNode nonVoid =
        classBody(field(false, "count"), method(false, INT, "setCount", "count", false));
    assertThat(run(new HiddenFieldCheck(false, true, MESSAGE), nonVoid)).hasSize(1);
  }

  @Test
  void constructorParameterIsSkippedOnlyWhenTheOptionIsOn() {
    final SyntaxNode constructor =
        classBody(field(false, "count"), method(false, "", "Violating", "count", true));
    assertThat(run(new HiddenFieldCheck(false, false, MESSAGE), constructor)).hasSize(1);
    assertThat(run(new HiddenFieldCheck(true, false, MESSAGE), constructor)).isEmpty();
  }

  private static List<RuleMatch> run(final HiddenFieldCheck check, final SyntaxNode root) {
    return check.check(root, FILE);
  }

  private static SyntaxNode classBody(final SyntaxNode... members) {
    return n(CLASS_BODY, members);
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
