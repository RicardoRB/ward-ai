package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The switch and exception coding checks on small trees: their options and boundary cases. */
class CodingChecksTest extends TreeBuilders {

  private static final String MESSAGE = "{value}";
  private static final String CASE = "case";
  private static final String DEFAULT = "default";
  private static final String NULL_LABEL = "null";
  private static final String FIRST_STATEMENT = "result=1;";
  private static final String LEAVING_STATEMENT = "break;";
  private static final String THROWING_STATEMENT = "throw e;";
  private static final String CALL_STATEMENT = "breakfast();";
  private static final String OTHER_STATEMENT = "x;";
  private static final String RUNTIME = "RuntimeException";
  private static final String TOKEN_CASE = "'case'";
  private static final String TOKEN_DEFAULT = "'default'";
  private static final String TOKEN_NAME = "IDENTIFIER";
  private static final String TOKEN_SWITCH = "'switch'";
  private static final String TOKEN_CLOSE_BRACE = "'}'";
  private static final String TOKEN_CATCH = "'catch'";
  private static final String TOKEN_THROWS = "'throws'";
  private static final String TOKEN_AT = "'@'";
  private static final String TOKEN_NULL = "NULL";
  private static final String GROUP = "switchBlockStatementGroup";
  private static final String LABEL = "switchLabel";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String STATEMENT = "statement";
  private static final String TYPE_TYPE = "typeType";
  private static final String QUALIFIED_NAME = "qualifiedName";
  private static final String QUALIFIED_NAME_LIST = "qualifiedNameList";
  private static final String CLASS_BODY_DECLARATION = "classBodyDeclaration";
  private static final String MEMBER = "memberDeclaration";
  private static final String MODIFIER = "modifier";
  private static final String CLASS_OR_INTERFACE_MODIFIER = "classOrInterfaceModifier";
  private static final String ANNOTATION = "annotation";
  private static final String METHOD = "methodDeclaration";
  private static final String VOID = "void";

  @Test
  void fallThroughReportsGroupsThatRunIntoTheNextLabel() {
    final SyntaxNode falls =
        switchOf(group(CASE, FIRST_STATEMENT), group(DEFAULT, OTHER_STATEMENT));
    assertThat(findings(new FallThroughCheck(false, MESSAGE), falls)).hasSize(1);
    final SyntaxNode leaves =
        switchOf(group(CASE, LEAVING_STATEMENT), group(DEFAULT, OTHER_STATEMENT));
    assertThat(findings(new FallThroughCheck(false, MESSAGE), leaves)).isEmpty();
    final SyntaxNode last = switchOf(group(CASE, FIRST_STATEMENT));
    assertThat(findings(new FallThroughCheck(false, MESSAGE), last)).isEmpty();
    assertThat(findings(new FallThroughCheck(true, MESSAGE), last)).hasSize(1);
    final SyntaxNode trailing = switchOf(group(CASE, THROWING_STATEMENT), labelOnly(DEFAULT));
    assertThat(findings(new FallThroughCheck(false, MESSAGE), trailing)).isEmpty();
    final SyntaxNode call = switchOf(group(CASE, CALL_STATEMENT), group(DEFAULT, OTHER_STATEMENT));
    assertThat(findings(new FallThroughCheck(false, MESSAGE), call)).hasSize(1);
  }

  @Test
  void missingSwitchDefaultSkipsSwitchesWithDefaultOrPatternOrNullLabels() {
    final SyntaxNode without = switchOf(group(CASE, OTHER_STATEMENT));
    assertThat(findings(new MissingSwitchDefaultCheck(MESSAGE), without)).hasSize(1);
    final SyntaxNode withDefault = switchOf(group(CASE, OTHER_STATEMENT), labelOnly(DEFAULT));
    assertThat(findings(new MissingSwitchDefaultCheck(MESSAGE), withDefault)).isEmpty();
    final SyntaxNode withNull = switchOf(group(CASE, OTHER_STATEMENT), labelOnly(NULL_LABEL));
    assertThat(findings(new MissingSwitchDefaultCheck(MESSAGE), withNull)).isEmpty();
    final SyntaxNode pattern =
        switchOf(
            group(CASE, OTHER_STATEMENT),
            n(LABEL, t(TOKEN_CASE, CASE), n(TYPE_TYPE, id("String"))));
    assertThat(findings(new MissingSwitchDefaultCheck(MESSAGE), pattern)).isEmpty();
  }

  @Test
  void illegalCatchAndThrowsReportOnlyTheConfiguredNames() {
    final SyntaxNode catching = catchClause("Error");
    assertThat(findings(new IllegalCatchCheck(Set.of("Error"), MESSAGE), catching)).hasSize(1);
    assertThat(findings(new IllegalCatchCheck(Set.of("IOException"), MESSAGE), catching)).isEmpty();
    final SyntaxNode throwing = throwing("run", RUNTIME);
    assertThat(findings(new IllegalThrowsCheck(Set.of(RUNTIME), Set.of(), true, MESSAGE), throwing))
        .hasSize(1);
    assertThat(
            findings(
                new IllegalThrowsCheck(Set.of(RUNTIME), Set.of("run"), true, MESSAGE), throwing))
        .isEmpty();
    final SyntaxNode overriding = overriding("run", RUNTIME);
    assertThat(
            findings(new IllegalThrowsCheck(Set.of(RUNTIME), Set.of(), true, MESSAGE), overriding))
        .isEmpty();
    assertThat(
            findings(new IllegalThrowsCheck(Set.of(RUNTIME), Set.of(), false, MESSAGE), overriding))
        .hasSize(1);
  }

  private static List<RuleMatch> findings(final UnitCheck check, final SyntaxNode root) {
    return check.check(root, SourceFile.UNKNOWN);
  }

  private static SyntaxNode switchOf(final SyntaxNode... groups) {
    final SyntaxNode[] children = new SyntaxNode[groups.length + 2];
    children[0] = t(TOKEN_SWITCH, "switch");
    System.arraycopy(groups, 0, children, 1, groups.length);
    children[groups.length + 1] = t(TOKEN_CLOSE_BRACE, "}");
    return n(STATEMENT, children);
  }

  /** A case group whose statement starts with its first word, as the grammar gives it. */
  private static SyntaxNode group(final String label, final String statement) {
    final String word = statement.replaceAll("[^A-Za-z].*", "");
    final String kind = Set.of("break", "throw").contains(word) ? "'" + word + "'" : TOKEN_NAME;
    return n(GROUP, labelOnly(label), n(BLOCK_STATEMENT, n(STATEMENT, t(kind, statement))));
  }

  private static SyntaxNode labelOnly(final String label) {
    if (NULL_LABEL.equals(label)) {
      return n(LABEL, t(TOKEN_CASE, CASE), t(TOKEN_NULL, NULL_LABEL));
    }
    return n(LABEL, t(CASE.equals(label) ? TOKEN_CASE : TOKEN_DEFAULT, label));
  }

  private static SyntaxNode catchClause(final String name) {
    return n(
        "catchClause",
        t(TOKEN_CATCH, "catch"),
        n("catchType", n(QUALIFIED_NAME, t(TOKEN_NAME, name))));
  }

  private static SyntaxNode throwing(final String name, final String thrown) {
    return n(
        METHOD,
        t(VOID, VOID),
        id(name),
        n("formalParameters"),
        t(TOKEN_THROWS, "throws"),
        n(QUALIFIED_NAME_LIST, n(QUALIFIED_NAME, t(TOKEN_NAME, thrown))));
  }

  private static SyntaxNode overriding(final String name, final String thrown) {
    return n(
        CLASS_BODY_DECLARATION,
        n(MODIFIER, n(CLASS_OR_INTERFACE_MODIFIER, n(ANNOTATION, t(TOKEN_AT, "@Override")))),
        n(MEMBER, throwing(name, thrown)));
  }
}
