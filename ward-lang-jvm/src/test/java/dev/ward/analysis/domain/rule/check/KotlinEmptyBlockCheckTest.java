package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.group;
import static dev.ward.analysis.domain.rule.check.TokenTrees.tok;
import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Runs the Kotlin empty-block check over hand-made trees of the shapes the grammar produces. */
class KotlinEmptyBlockCheckTest {

  private static final String MESSAGE = "m";
  private static final String IF = "ifExpression";
  private static final String BLOCK = "block";
  private static final String BODY = "controlStructureBody";
  private static final String OPEN = "'{'";
  private static final String CLOSE = "'}'";
  private static final String RPAREN = "RPAREN";
  private static final String ELSE = "'else'";
  private static final int LINE = 1;
  private static final int OFFSET = 0;

  private static SyntaxNode leaf(final String kind, final String text) {
    return tok(kind, text, LINE, OFFSET);
  }

  private static SyntaxNode emptyBlock() {
    return group(BLOCK, leaf(OPEN, "{"), leaf(CLOSE, "}"));
  }

  private static SyntaxNode ifWithBody(final SyntaxNode body) {
    return group(IF, leaf("'if'", "if"), leaf(RPAREN, ")"), group(BODY, body));
  }

  @Test
  void reportsAnEmptyIfBlockButNotACommentedOne() {
    final KotlinEmptyBlockCheck check = new KotlinEmptyBlockCheck(Set.of(IF), RPAREN, MESSAGE);

    assertThat(unitMatches(check, ifWithBody(emptyBlock()))).hasSize(1);
    final SyntaxNode commented =
        group(BLOCK, leaf(OPEN, "{"), leaf("comment", "// x"), leaf(CLOSE, "}"));
    assertThat(unitMatches(check, ifWithBody(commented))).isEmpty();
  }

  @Test
  void reportsAnEmptyBraceBodyThatTheParserReadsAsAFunctionLiteral() {
    final SyntaxNode literal = group("functionLiteral", leaf(OPEN, "{"), leaf(CLOSE, "}"));
    final SyntaxNode body = group(BODY, group("expression", literal));

    assertThat(
            unitMatches(new KotlinEmptyBlockCheck(Set.of(IF), RPAREN, MESSAGE), ifWithBody(body)))
        .hasSize(1);
  }

  @Test
  void reportsAnEmptyBodyWhenItIsOwnBlockOrOwnerWhenItHasNone() {
    assertThat(
            unitMatches(
                new KotlinEmptyBlockCheck(Set.of("anonymousInitializer"), "", MESSAGE),
                group("anonymousInitializer", leaf("'init'", "init"), emptyBlock())))
        .hasSize(1);
    assertThat(
            unitMatches(
                new KotlinEmptyBlockCheck(Set.of("kotlinFile"), "", MESSAGE),
                group("kotlinFile", leaf("EOF", "<EOF>"))))
        .hasSize(1);
    assertThat(
            unitMatches(
                new KotlinEmptyBlockCheck(Set.of("kotlinFile"), "", MESSAGE),
                group("kotlinFile", leaf("'package'", "package"))))
        .isEmpty();
  }

  @Test
  void stopsAtAnElseSoAThenBodyIsNotTakenFromTheElseBranch() {
    final SyntaxNode owner =
        group(
            IF,
            leaf("'if'", "if"),
            leaf(RPAREN, ")"),
            leaf(ELSE, "else"),
            group(BODY, emptyBlock()));

    assertThat(unitMatches(new KotlinEmptyBlockCheck(Set.of(IF), RPAREN, MESSAGE), owner))
        .isEmpty();
    assertThat(unitMatches(new KotlinEmptyBlockCheck(Set.of(IF), ELSE, MESSAGE), owner)).hasSize(1);
  }
}
