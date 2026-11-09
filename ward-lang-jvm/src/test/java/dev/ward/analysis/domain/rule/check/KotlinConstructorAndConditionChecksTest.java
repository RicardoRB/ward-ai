package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.group;
import static dev.ward.analysis.domain.rule.check.TokenTrees.tok;
import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Runs the Kotlin empty-when, empty-constructor and complex-condition checks over hand trees. */
class KotlinConstructorAndConditionChecksTest {

  private static final String MESSAGE = "m";
  private static final String LINE_TEXT = "token";
  private static final int LINE = 1;
  private static final int OFFSET = 0;
  private static final int THRESHOLD = 4;

  private static SyntaxNode leaf(final String kind, final String text) {
    return tok(kind, text, LINE, OFFSET);
  }

  private static SyntaxNode bareClass(final SyntaxNode... constructorParts) {
    return group(
        "classDeclaration",
        leaf("'class'", "class"),
        leaf("simpleIdentifier", "A"),
        group("primaryConstructor", constructorParts));
  }

  @Test
  void keepsAWhenWithACommentOnly() {
    final SyntaxNode empty = group("whenExpression", leaf("'when'", "when"), leaf("'{'", "{"));
    final SyntaxNode commented =
        group("whenExpression", leaf("'when'", "when"), leaf("comment", "// x"));

    assertThat(unitMatches(new KotlinEmptyWhenCheck(MESSAGE), empty)).hasSize(1);
    assertThat(unitMatches(new KotlinEmptyWhenCheck(MESSAGE), commented)).isEmpty();
  }

  @Test
  void reportsOnlyAPlainEmptyPrimaryConstructor() {
    final KotlinEmptyConstructorCheck check = new KotlinEmptyConstructorCheck(MESSAGE);

    assertThat(unitMatches(check, bareClass(leaf("'('", "("), leaf("')'", ")")))).hasSize(1);
    assertThat(
            unitMatches(
                check,
                bareClass(
                    group("modifierList", leaf("annotation", "@Inject")), leaf(LINE_TEXT, "()"))))
        .isEmpty();
    final SyntaxNode expect =
        group(
            "classDeclaration",
            group("modifierList", leaf(LINE_TEXT, "expect")),
            leaf("'class'", "class"),
            group("primaryConstructor", leaf("'('", "("), leaf("')'", ")")));
    assertThat(unitMatches(check, expect)).isEmpty();
  }

  @Test
  void keepsAClassWhoseSecondaryConstructorCallsThis() {
    final SyntaxNode delegation =
        group(
            "constructorDelegationCall",
            leaf("'this'", "this"),
            leaf("'('", "("),
            leaf("')'", ")"));
    final SyntaxNode secondary = group("secondaryConstructor", delegation);
    final SyntaxNode withThis =
        group(
            "classDeclaration",
            leaf("'class'", "class"),
            group("primaryConstructor", leaf("'('", "("), leaf("')'", ")")),
            group("classBody", group("classMemberDeclaration", secondary)));

    assertThat(unitMatches(new KotlinEmptyConstructorCheck(MESSAGE), withThis)).isEmpty();
  }

  @Test
  void leavesTheOperatorsOfALambdaOutOfTheConditionCount() {
    final SyntaxNode lambda =
        group(
            "functionLiteral",
            leaf(LINE_TEXT, "a"),
            leaf("'&&'", "&&"),
            leaf(LINE_TEXT, "b"),
            leaf("'&&'", "&&"),
            leaf(LINE_TEXT, "c"));
    final SyntaxNode condition =
        group("expression", lambda, leaf("'&&'", "&&"), leaf(LINE_TEXT, "y"));
    final SyntaxNode statement = group("ifExpression", leaf("'if'", "if"), condition);

    assertThat(
            unitMatches(
                new KotlinComplexConditionCheck(Set.of("ifExpression"), THRESHOLD, MESSAGE),
                statement))
        .isEmpty();
  }
}
