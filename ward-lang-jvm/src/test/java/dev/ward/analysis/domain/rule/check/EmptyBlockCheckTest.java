package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.fileMatches;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

class EmptyBlockCheckTest {

  private static final String STATEMENT = "statement";
  private static final String STATEMENT_OPTION = "statement";
  private static final String TEXT_OPTION = "text";
  private static final String MESSAGE = "message";
  private static final String SOURCE = "if (x) { // note\n}";
  private static final int COMMENT_BLOCK_START = 9;
  private static final int COMMENT_BLOCK_END = 18;

  @Test
  void reportsEmptyBodiesUnderTheStatementOption() {
    assertThat(run(STATEMENT_OPTION, ifStatement(emptyBlock(0, 0)), "")).hasSize(1);
    assertThat(
            run(
                STATEMENT_OPTION,
                TreeBuilders.n(
                    STATEMENT, TreeBuilders.t("'try'", "try"), wrapped(emptyBlock(0, 0))),
                ""))
        .hasSize(1);
    assertThat(run(STATEMENT_OPTION, TreeBuilders.n("finallyBlock", emptyBlock(0, 0)), ""))
        .hasSize(1);
    assertThat(run(STATEMENT_OPTION, TreeBuilders.n("classBodyDeclaration", emptyBlock(0, 0)), ""))
        .hasSize(1);
  }

  @Test
  void ignoresBlocksThatHoldStatementsAndUnrelatedStatements() {
    final SyntaxNode filled =
        TreeBuilders.n(
            "block", TreeBuilders.n("blockStatement", TreeBuilders.t("IDENTIFIER", "x")));
    assertThat(run(STATEMENT_OPTION, ifStatement(filled), "")).isEmpty();
    assertThat(
            run(
                STATEMENT_OPTION,
                TreeBuilders.n(STATEMENT, TreeBuilders.t("'return'", "return"), emptyBlock(0, 0)),
                ""))
        .isEmpty();
    assertThat(run(STATEMENT_OPTION, TreeBuilders.n(STATEMENT), "")).isEmpty();
    assertThat(
            run(
                STATEMENT_OPTION,
                TreeBuilders.n(
                    STATEMENT, TreeBuilders.t("'while'", "while"), TreeBuilders.n(STATEMENT)),
                ""))
        .isEmpty();
    assertThat(
            run(
                STATEMENT_OPTION,
                TreeBuilders.n("classBodyDeclaration", TreeBuilders.t("';'", ";")),
                ""))
        .isEmpty();
  }

  @Test
  void acceptsACommentInsideAnEmptyBlockOnlyUnderTheTextOption() {
    final SyntaxNode root = ifStatement(emptyBlock(COMMENT_BLOCK_START, COMMENT_BLOCK_END));
    assertThat(run(TEXT_OPTION, root, SOURCE)).isEmpty();
    assertThat(run(STATEMENT_OPTION, root, SOURCE)).hasSize(1);
    assertThat(run(TEXT_OPTION, ifStatement(emptyBlock(0, 2)), "{}")).hasSize(1);
    assertThat(run(TEXT_OPTION, ifStatement(emptyBlock(0, 0)), SOURCE)).hasSize(1);
  }

  @Test
  void rejectsAnUnknownOption() {
    assertThatThrownBy(() -> new EmptyBlockCheck("maybe", MESSAGE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maybe");
  }

  private static java.util.List<dev.ward.analysis.domain.rule.RuleMatch> run(
      final String option, final SyntaxNode root, final String text) {
    return fileMatches(new EmptyBlockCheck(option, MESSAGE), text, root);
  }

  private static SyntaxNode emptyBlock(final int start, final int end) {
    return Trees.node("block", start, end, 1);
  }

  private static SyntaxNode ifStatement(final SyntaxNode body) {
    return TreeBuilders.n(
        STATEMENT, TreeBuilders.t("'if'", "if"), TreeBuilders.t("'('", "("), body);
  }

  private static SyntaxNode wrapped(final SyntaxNode block) {
    return TreeBuilders.n(STATEMENT, block);
  }
}
