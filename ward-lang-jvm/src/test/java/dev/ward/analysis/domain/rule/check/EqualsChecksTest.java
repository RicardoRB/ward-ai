package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The equals, hashCode and return-count coding checks on small trees. */
class EqualsChecksTest extends TreeBuilders {

  private static final String MESSAGE = "{value}";
  private static final String OBJECT = "Object";
  private static final String VOID = "void";
  private static final String INT = "int";
  private static final String EQUALS = "equals";
  private static final String HASH_CODE = "hashCode";
  private static final String EQUALS_IGNORE_CASE = "equalsIgnoreCase";
  private static final String RETURN = "'return'";
  private static final String STATEMENT = "statement";
  private static final String LAMBDA = "lambdaExpression";
  private static final String TYPE_TYPE = "typeType";
  private static final String TYPE_TYPE_OR_VOID = "typeTypeOrVoid";
  private static final String FORMAL_PARAMETER = "formalParameter";
  private static final String FORMAL_PARAMETERS = "formalParameters";
  private static final String TOKEN_NAME = "IDENTIFIER";
  private static final String TOKEN_STRING = "STRING";
  private static final String ARGUMENTS = "arguments";
  private static final String METHOD = "methodDeclaration";
  private static final int MAX_RETURNS = 2;
  private static final int MAX_RETURNS_FOR_VOID = 1;
  private static final int LEADING_CHILDREN = 3;

  @Test
  void equalsMethodsNeedTheirCounterpart() {
    final SyntaxNode equalsOnly = type(method(EQUALS, List.of(OBJECT)));
    assertThat(findings(new EqualsHashCodeCheck(MESSAGE), equalsOnly)).hasSize(1);
    final SyntaxNode hashOnly = type(method(HASH_CODE, List.of()));
    assertThat(findings(new EqualsHashCodeCheck(MESSAGE), hashOnly)).hasSize(1);
    final SyntaxNode both = type(method(EQUALS, List.of(OBJECT)), method(HASH_CODE, List.of()));
    assertThat(findings(new EqualsHashCodeCheck(MESSAGE), both)).isEmpty();
    final SyntaxNode covariant = type(method(EQUALS, List.of("Pair")));
    assertThat(findings(new CovariantEqualsCheck(MESSAGE), covariant)).hasSize(1);
    final SyntaxNode overloaded =
        type(method(EQUALS, List.of("Pair")), method(EQUALS, List.of(OBJECT)));
    assertThat(findings(new CovariantEqualsCheck(MESSAGE), overloaded)).isEmpty();
  }

  @Test
  void equalsAvoidNullReportsLiteralArgumentsOnly() {
    assertThat(findings(new EqualsAvoidNullCheck(false, MESSAGE), equalsCall(EQUALS))).hasSize(1);
    final SyntaxNode ignoreCase = equalsCall(EQUALS_IGNORE_CASE);
    assertThat(findings(new EqualsAvoidNullCheck(false, MESSAGE), ignoreCase)).hasSize(1);
    assertThat(findings(new EqualsAvoidNullCheck(true, MESSAGE), ignoreCase)).isEmpty();
    assertThat(findings(new EqualsAvoidNullCheck(false, MESSAGE), equalsCall(HASH_CODE))).isEmpty();
  }

  @Test
  void returnCountUsesTheLimitOfTheMethodsReturnType() {
    final SyntaxNode voidOnce = returns(VOID, returnStatement(), lambdaReturn());
    assertThat(findings(new ReturnCountCheck(MAX_RETURNS, MAX_RETURNS_FOR_VOID, MESSAGE), voidOnce))
        .isEmpty();
    final SyntaxNode voidTwice = returns(VOID, returnStatement(), returnStatement());
    assertThat(
            findings(new ReturnCountCheck(MAX_RETURNS, MAX_RETURNS_FOR_VOID, MESSAGE), voidTwice))
        .hasSize(1);
    assertThat(findings(new ReturnCountCheck(MAX_RETURNS, MAX_RETURNS, MESSAGE), voidTwice))
        .isEmpty();
    final SyntaxNode valued = returns(INT, returnStatement(), returnStatement(), returnStatement());
    assertThat(findings(new ReturnCountCheck(MAX_RETURNS, MAX_RETURNS_FOR_VOID, MESSAGE), valued))
        .hasSize(1);
  }

  private static List<RuleMatch> findings(final UnitCheck check, final SyntaxNode root) {
    return check.check(root, SourceFile.UNKNOWN);
  }

  private static SyntaxNode type(final SyntaxNode... methods) {
    final SyntaxNode[] declarations = new SyntaxNode[methods.length];
    for (int index = 0; index < methods.length; index++) {
      declarations[index] = n("classBodyDeclaration", n("memberDeclaration", methods[index]));
    }
    return n("classDeclaration", n("classBody", declarations));
  }

  private static SyntaxNode method(final String name, final List<String> types) {
    final SyntaxNode[] parameters = new SyntaxNode[types.size()];
    for (int index = 0; index < types.size(); index++) {
      parameters[index] = n(FORMAL_PARAMETER, n(TYPE_TYPE, t(TOKEN_NAME, types.get(index))));
    }
    return n(METHOD, t(VOID, VOID), id(name), n(FORMAL_PARAMETERS, parameters));
  }

  private static SyntaxNode equalsCall(final String name) {
    return n(
        EXPRESSION,
        n(EXPRESSION, n(PRIMARY, id("value"))),
        t(TOKEN_DOT, "."),
        n(
            METHOD_CALL,
            id(name),
            n(ARGUMENTS, t(TOKEN_OPEN, "("), t(TOKEN_STRING, "\"ward\""), t(TOKEN_CLOSE, ")"))));
  }

  private static SyntaxNode returns(final String type, final SyntaxNode... statements) {
    final SyntaxNode[] children = new SyntaxNode[statements.length + LEADING_CHILDREN];
    children[0] = n(TYPE_TYPE_OR_VOID, t(type, type));
    children[1] = id("run");
    children[2] = n(FORMAL_PARAMETERS);
    System.arraycopy(statements, 0, children, LEADING_CHILDREN, statements.length);
    return n(METHOD, children);
  }

  private static SyntaxNode returnStatement() {
    return n(STATEMENT, t(RETURN, "return;"));
  }

  private static SyntaxNode lambdaReturn() {
    return n(LAMBDA, returnStatement());
  }
}
