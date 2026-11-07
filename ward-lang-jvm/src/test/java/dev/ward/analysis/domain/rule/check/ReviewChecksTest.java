package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewChecksTest extends TreeBuilders {

  @Test
  void anUnusedLabelIsReportedAndAUsedOneIsNot() {
    final SyntaxNode unused =
        unit(
            n(
                STATEMENT,
                id("outer"),
                t("':'", ":"),
                n(STATEMENT, n(STATEMENT, t("'for'", "for")))));
    final SyntaxNode used =
        unit(
            n(
                STATEMENT,
                id("outer"),
                t("':'", ":"),
                n(
                    STATEMENT,
                    n(STATEMENT, t("'break'", "break"), id("outer"), t(TOKEN_SEMICOLON, ";")))));
    assertThat(run(new UnusedLabelCheck(MESSAGE), unused)).hasSize(1);
    assertThat(run(new UnusedLabelCheck(MESSAGE), used)).isEmpty();
  }

  @Test
  void aDefaultLabelFollowedByAnotherLabelIsReported() {
    final SyntaxNode misplaced =
        unit(
            n(
                STATEMENT,
                t(SWITCH, "switch"),
                n(
                    "switchBlockStatementGroup",
                    n("switchLabel", t("'default'", "default"), t("':'", ":"))),
                n(
                    "switchBlockStatementGroup",
                    n("switchLabel", t("'case'", "case"), t("':'", ":")))));
    final SyntaxNode trailing =
        unit(
            n(
                STATEMENT,
                t(SWITCH, "switch"),
                n("switchLabel", t("'case'", "case"), t("':'", ":")),
                n("switchLabel", t("'default'", "default"), t("':'", ":"))));
    assertThat(run(new DefaultLabelNotLastCheck(MESSAGE), misplaced)).hasSize(1);
    assertThat(run(new DefaultLabelNotLastCheck(MESSAGE), trailing)).isEmpty();
  }

  @Test
  void anAbstractClassWithoutAbstractMethodIsReported() {
    final SyntaxNode bare = Declarations.abstractClass(List.of(Declarations.member(false)));
    final SyntaxNode withAbstract = Declarations.abstractClass(List.of(Declarations.member(true)));
    assertThat(run(new AbstractClassWithoutAbstractMethodCheck(MESSAGE), bare)).hasSize(1);
    assertThat(run(new AbstractClassWithoutAbstractMethodCheck(MESSAGE), withAbstract)).isEmpty();
  }

  @Test
  void anInterfaceWithOneAbstractMethodNeedsTheAnnotation() {
    final SyntaxNode bare = Declarations.interfaceUnit(List.of(), List.of("greet"));
    final SyntaxNode annotated =
        Declarations.interfaceUnit(
            List.of(
                n(
                    "classOrInterfaceModifier",
                    n(ANNOTATION, t("'@'", "@"), id("FunctionalInterface")))),
            List.of("greet"));
    final SyntaxNode two = Declarations.interfaceUnit(List.of(), List.of("a", "b"));
    assertThat(run(new ImplicitFunctionalInterfaceCheck(MESSAGE), bare)).hasSize(1);
    assertThat(run(new ImplicitFunctionalInterfaceCheck(MESSAGE), annotated)).isEmpty();
    assertThat(run(new ImplicitFunctionalInterfaceCheck(MESSAGE), two)).isEmpty();
  }

  @Test
  void aThrowThatDropsTheCaughtExceptionIsReported() {
    final SyntaxNode dropped = unit(catchWithThrow(argument("e.getMessage()")));
    final SyntaxNode kept = unit(catchWithThrow(argument("e")));
    assertThat(run(new PreserveStackTraceCheck(MESSAGE), dropped)).hasSize(1);
    assertThat(run(new PreserveStackTraceCheck(MESSAGE), kept)).isEmpty();
  }

  @Test
  void assigningACatchParameterIsReported() {
    final SyntaxNode assigned =
        unit(n("catchClause", t("'catch'", "catch"), id("e"), n(BLOCK, assignment("e", "x"))));
    final SyntaxNode other = unit(n("catchClause", id("e"), n(BLOCK, assignment("other", "x"))));
    assertThat(run(new AvoidReassigningCatchVariablesCheck(MESSAGE), assigned)).hasSize(1);
    assertThat(run(new AvoidReassigningCatchVariablesCheck(MESSAGE), other)).isEmpty();
  }

  @Test
  void assigningAClassicLoopVariableIsReportedOnlyWhenDenied() {
    final SyntaxNode loop =
        unit(
            n(
                STATEMENT,
                t("'for'", "for"),
                n(
                    "forControl",
                    n(
                        "forInit",
                        n(
                            "localVariableDeclaration",
                            n("variableDeclarator", n(PARAMETER_ID, id("i")))))),
                n(STATEMENT, n(BLOCK, assignment("i", "x")))));
    assertThat(run(new AvoidReassigningLoopVariablesCheck("deny", "deny", MESSAGE), loop))
        .hasSize(1);
    assertThat(run(new AvoidReassigningLoopVariablesCheck("allow", "deny", MESSAGE), loop))
        .isEmpty();
  }

  @Test
  void assigningAnEnhancedLoopVariableRespectsFirstOnly() {
    final SyntaxNode loop =
        unit(
            n(
                STATEMENT,
                t("'for'", "for"),
                n(
                    "forControl",
                    n("enhancedForControl", n(PARAMETER_ID, id("v")), t("':'", ":"), id("xs"))),
                n(
                    STATEMENT,
                    n(
                        BLOCK,
                        n("blockStatement", assignment("v", "x")),
                        n("blockStatement", assignment("v", "y"))))));
    assertThat(run(new AvoidReassigningLoopVariablesCheck("allow", "firstOnly", MESSAGE), loop))
        .hasSize(1);
    assertThat(run(new AvoidReassigningLoopVariablesCheck("allow", "deny", MESSAGE), loop))
        .hasSize(2);
  }

  @Test
  void returningAnArrayFieldIsReported() {
    final SyntaxNode returns =
        unit(
            Declarations.classWith(
                Declarations.arrayField("values"), Declarations.returnOf("values")));
    final SyntaxNode returnsCopy =
        unit(
            Declarations.classWith(
                Declarations.arrayField("values"), Declarations.returnOf("copy")));
    assertThat(run(new MethodReturnsInternalArrayCheck(MESSAGE), returns)).hasSize(1);
    assertThat(run(new MethodReturnsInternalArrayCheck(MESSAGE), returnsCopy)).isEmpty();
  }

  private static List<RuleMatch> run(final UnitCheck check, final SyntaxNode root) {
    return check.check(root, FILE);
  }
}
