package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Checkstyle-style coding checks on small trees: their options and boundary cases. */
class CodingChecksTest extends TreeBuilders {

  private static final String MESSAGE = "{value}";
  private static final String TOKEN_CASE = "'case'";
  private static final String TOKEN_DEFAULT = "'default'";
  private static final String TOKEN_RETURN = "'return'";
  private static final String TOKEN_THROWS = "'throws'";
  private static final String TOKEN_CATCH = "'catch'";
  private static final String TOKEN_SWITCH = "'switch'";
  private static final String TOKEN_CLOSE_BRACE = "'}'";
  private static final String TOKEN_AT = "'@'";
  private static final String TOKEN_NULL = "NULL";
  private static final String TOKEN_NAME = "IDENTIFIER";
  private static final String TOKEN_STRING = "STRING";
  private static final String VOID = "void";
  private static final String OBJECT = "Object";
  private static final String GROUP = "switchBlockStatementGroup";
  private static final String LABEL = "switchLabel";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String CATCH_CLAUSE = "catchClause";
  private static final String CATCH_TYPE = "catchType";
  private static final String QUALIFIED_NAME = "qualifiedName";
  private static final String QUALIFIED_NAME_LIST = "qualifiedNameList";
  private static final String FORMAL_PARAMETERS_KIND = "formalParameters";
  private static final String ARGUMENTS = "arguments";
  private static final String CLASS_BODY = "classBody";
  private static final String CLASS_BODY_DECLARATION_KIND = "classBodyDeclaration";
  private static final String MEMBER = "memberDeclaration";
  private static final String TYPE_TYPE = "typeType";
  private static final String TYPE_TYPE_OR_VOID = "typeTypeOrVoid";
  private static final String LAMBDA = "lambdaExpression";
  private static final String ANNOTATION = "annotation";
  private static final String MODIFIER = "modifier";
  private static final String CLASS_OR_INTERFACE_MODIFIER = "classOrInterfaceModifier";

  @Test
  void fallThroughReportsGroupsThatRunIntoTheNextLabel() {
    final SyntaxNode falls = switchOf(group("case", "result=1;"), group("default", "return2;"));
    assertThat(findings(new FallThroughCheck(false, MESSAGE), falls)).hasSize(1);
    final SyntaxNode leaves = switchOf(group("case", "break;"), group("default", "return2;"));
    assertThat(findings(new FallThroughCheck(false, MESSAGE), leaves)).isEmpty();
    final SyntaxNode last = switchOf(group("case", "result=1;"));
    assertThat(findings(new FallThroughCheck(false, MESSAGE), last)).isEmpty();
    assertThat(findings(new FallThroughCheck(true, MESSAGE), last)).hasSize(1);
    final SyntaxNode trailing = switchOf(group("case", "throw e;"), labelOnly("default"));
    assertThat(findings(new FallThroughCheck(false, MESSAGE), trailing)).isEmpty();
    final SyntaxNode call = switchOf(group("case", "breakfast();"), group("default", "x;"));
    assertThat(findings(new FallThroughCheck(false, MESSAGE), call)).hasSize(1);
  }

  @Test
  void missingSwitchDefaultSkipsSwitchesWithDefaultOrPatternOrNullLabels() {
    final SyntaxNode without = switchOf(group("case", "x;"));
    assertThat(findings(new MissingSwitchDefaultCheck(MESSAGE), without)).hasSize(1);
    final SyntaxNode withDefault = switchOf(group("case", "x;"), labelOnly("default"));
    assertThat(findings(new MissingSwitchDefaultCheck(MESSAGE), withDefault)).isEmpty();
    final SyntaxNode withNull = switchOf(group("case", "x;"), labelOnly(TOKEN_NULL));
    assertThat(findings(new MissingSwitchDefaultCheck(MESSAGE), withNull)).isEmpty();
    final SyntaxNode pattern =
        switchOf(group("case", "x;"), n(LABEL, t(TOKEN_CASE, "case"), n(TYPE_TYPE, id("String"))));
    assertThat(findings(new MissingSwitchDefaultCheck(MESSAGE), pattern)).isEmpty();
  }

  @Test
  void illegalCatchAndThrowsReportOnlyTheConfiguredNames() {
    final SyntaxNode catching = catchClause("Error");
    assertThat(findings(new IllegalCatchCheck(Set.of("Error"), MESSAGE), catching)).hasSize(1);
    assertThat(findings(new IllegalCatchCheck(Set.of("IOException"), MESSAGE), catching)).isEmpty();
    final SyntaxNode throwing = throwing("run", "RuntimeException");
    assertThat(findings(new IllegalThrowsCheck(illegal(), Set.of(), true, MESSAGE), throwing))
        .hasSize(1);
    assertThat(findings(new IllegalThrowsCheck(illegal(), Set.of("run"), true, MESSAGE), throwing))
        .isEmpty();
    final SyntaxNode overriding = overriding("run", "RuntimeException");
    assertThat(findings(new IllegalThrowsCheck(illegal(), Set.of(), true, MESSAGE), overriding))
        .isEmpty();
    assertThat(findings(new IllegalThrowsCheck(illegal(), Set.of(), false, MESSAGE), overriding))
        .hasSize(1);
  }

  @Test
  void equalsMethodsNeedTheirCounterpart() {
    final SyntaxNode equalsOnly = type(method("equals", List.of(OBJECT)));
    assertThat(findings(new EqualsHashCodeCheck(MESSAGE), equalsOnly)).hasSize(1);
    final SyntaxNode hashOnly = type(method("hashCode", List.of()));
    assertThat(findings(new EqualsHashCodeCheck(MESSAGE), hashOnly)).hasSize(1);
    final SyntaxNode both = type(method("equals", List.of(OBJECT)), method("hashCode", List.of()));
    assertThat(findings(new EqualsHashCodeCheck(MESSAGE), both)).isEmpty();
    final SyntaxNode covariant = type(method("equals", List.of("Pair")));
    assertThat(findings(new CovariantEqualsCheck(MESSAGE), covariant)).hasSize(1);
    final SyntaxNode overloaded =
        type(method("equals", List.of("Pair")), method("equals", List.of(OBJECT)));
    assertThat(findings(new CovariantEqualsCheck(MESSAGE), overloaded)).isEmpty();
  }

  @Test
  void equalsAvoidNullReportsLiteralArgumentsOnly() {
    assertThat(findings(new EqualsAvoidNullCheck(false, MESSAGE), equalsCall("equals"))).hasSize(1);
    final SyntaxNode ignoreCase = equalsCall("equalsIgnoreCase");
    assertThat(findings(new EqualsAvoidNullCheck(false, MESSAGE), ignoreCase)).hasSize(1);
    assertThat(findings(new EqualsAvoidNullCheck(true, MESSAGE), ignoreCase)).isEmpty();
    assertThat(findings(new EqualsAvoidNullCheck(false, MESSAGE), equalsCall("hashCode")))
        .isEmpty();
  }

  @Test
  void returnCountUsesTheLimitOfTheMethodsReturnType() {
    final SyntaxNode voidOnce = returns(VOID, returnStatement(), lambdaReturn());
    assertThat(findings(new ReturnCountCheck(2, 1, MESSAGE), voidOnce)).isEmpty();
    final SyntaxNode voidTwice = returns(VOID, returnStatement(), returnStatement());
    assertThat(findings(new ReturnCountCheck(2, 1, MESSAGE), voidTwice)).hasSize(1);
    assertThat(findings(new ReturnCountCheck(2, 2, MESSAGE), voidTwice)).isEmpty();
    final SyntaxNode valued =
        returns("int", returnStatement(), returnStatement(), returnStatement());
    assertThat(findings(new ReturnCountCheck(2, 1, MESSAGE), valued)).hasSize(1);
  }

  private static Set<String> illegal() {
    return Set.of("RuntimeException");
  }

  private static List<RuleMatch> findings(final UnitCheck check, final SyntaxNode root) {
    return check.check(root, SourceFile.UNKNOWN);
  }

  private static SyntaxNode switchOf(final SyntaxNode... groups) {
    final SyntaxNode[] children = new SyntaxNode[groups.length + 2];
    children[0] = t(TOKEN_SWITCH, "switch");
    System.arraycopy(groups, 0, children, 1, groups.length);
    children[groups.length + 1] = t(TOKEN_CLOSE_BRACE, "}");
    return n("statement", children);
  }

  private static SyntaxNode group(final String label, final String statement) {
    return n(GROUP, labelOnly(label), n(BLOCK_STATEMENT, n("statement", leafFor(statement))));
  }

  /** A leaf whose first token is the statement's first word, as the grammar gives it. */
  private static SyntaxNode leafFor(final String statement) {
    final String word = statement.replaceAll("[^A-Za-z].*", "");
    final String kind =
        Set.of("break", "return", "throw").contains(word) ? "'" + word + "'" : TOKEN_NAME;
    return t(kind, statement);
  }

  private static SyntaxNode labelOnly(final String label) {
    if (TOKEN_NULL.equals(label)) {
      return n(LABEL, t(TOKEN_CASE, "case"), t(TOKEN_NULL, "null"));
    }
    final String keyword = "case".equals(label) ? TOKEN_CASE : TOKEN_DEFAULT;
    return n(LABEL, t(keyword, label));
  }

  private static SyntaxNode returnStatement() {
    return n("statement", t(TOKEN_RETURN, "return;"));
  }

  private static SyntaxNode lambdaReturn() {
    return n(LAMBDA, returnStatement());
  }

  private static SyntaxNode catchClause(final String name) {
    return n(
        CATCH_CLAUSE,
        t(TOKEN_CATCH, "catch"),
        n(CATCH_TYPE, n(QUALIFIED_NAME, t(TOKEN_NAME, name))));
  }

  private static SyntaxNode method(final String name, final List<String> types) {
    return n("methodDeclaration", t(VOID, VOID), id(name), parameters(types));
  }

  private static SyntaxNode throwing(final String name, final String thrown) {
    return n(
        "methodDeclaration",
        t(VOID, VOID),
        id(name),
        parameters(List.of()),
        t(TOKEN_THROWS, "throws"),
        n(QUALIFIED_NAME_LIST, n(QUALIFIED_NAME, t(TOKEN_NAME, thrown))));
  }

  private static SyntaxNode parameters(final List<String> types) {
    final SyntaxNode[] parameters = new SyntaxNode[types.size()];
    for (int index = 0; index < types.size(); index++) {
      parameters[index] = n("formalParameter", n(TYPE_TYPE, t(TOKEN_NAME, types.get(index))));
    }
    return n(FORMAL_PARAMETERS_KIND, parameters);
  }

  private static SyntaxNode overriding(final String name, final String thrown) {
    return n(
        CLASS_BODY_DECLARATION_KIND,
        n(MODIFIER, n(CLASS_OR_INTERFACE_MODIFIER, n(ANNOTATION, t(TOKEN_AT, "@Override")))),
        n(MEMBER, throwing(name, thrown)));
  }

  private static SyntaxNode type(final SyntaxNode... methods) {
    final SyntaxNode[] declarations = new SyntaxNode[methods.length];
    for (int index = 0; index < methods.length; index++) {
      declarations[index] = n(CLASS_BODY_DECLARATION_KIND, n(MEMBER, methods[index]));
    }
    return n("classDeclaration", n(CLASS_BODY, declarations));
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
    final SyntaxNode[] children = new SyntaxNode[statements.length + 3];
    children[0] = n(TYPE_TYPE_OR_VOID, t(type, type));
    children[1] = id("run");
    children[2] = parameters(List.of());
    System.arraycopy(statements, 0, children, 3, statements.length);
    return n("methodDeclaration", children);
  }
}
