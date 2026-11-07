package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static dev.ward.analysis.domain.rule.check.TreeBuilders.n;
import static dev.ward.analysis.domain.rule.check.TreeBuilders.t;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class NeedBracesCheckTest {

  private static final String MESSAGE = "message";
  private static final String STATEMENT = "statement";
  private static final String IF = "'if'";
  private static final String ELSE = "'else'";
  private static final String FOR = "'for'";
  private static final String WHILE = "'while'";
  private static final String DO = "'do'";
  private static final String SEMICOLON = "';'";
  private static final String IDENTIFIER = "IDENTIFIER";

  @Test
  void reportsIfAndLoopBodiesThatAreNotBlocks() {
    assertThat(run(false, n(STATEMENT, t(IF, "if"), bareBody()))).hasSize(1);
    assertThat(run(false, n(STATEMENT, t(WHILE, "while"), bareBody()))).hasSize(1);
    assertThat(run(false, n(STATEMENT, t(DO, "do"), bareBody()))).hasSize(1);
    assertThat(run(false, n(STATEMENT, t(IF, "if"), n(STATEMENT)))).hasSize(1);
    assertThat(run(false, n(STATEMENT, t(IF, "if"), block()))).isEmpty();
  }

  @Test
  void reportsAnElseBodyThatIsNeitherABlockNorAnIf() {
    final SyntaxNode notIf = n(STATEMENT, t(IF, "if"), block(), t(ELSE, "else"), bareBody());
    assertThat(run(false, notIf)).hasSize(1);
    final SyntaxNode elseIf =
        n(STATEMENT, t(IF, "if"), block(), t(ELSE, "else"), n(STATEMENT, t(IF, "if"), block()));
    assertThat(run(false, elseIf)).isEmpty();
  }

  @Test
  void allowsEmptyLoopBodiesOnlyWhenAskedAndNeverForIfs() {
    final SyntaxNode loop = n(STATEMENT, t(FOR, "for"), emptyBody());
    assertThat(run(false, loop)).hasSize(1);
    assertThat(run(true, loop)).isEmpty();
    assertThat(run(true, n(STATEMENT, t(IF, "if"), emptyBody()))).hasSize(1);
  }

  @Test
  void ignoresStatementsThatAreNotControlFlow() {
    assertThat(run(false, n(STATEMENT, t("'return'", "return"), bareBody()))).isEmpty();
    assertThat(run(false, n(STATEMENT))).isEmpty();
  }

  private static List<RuleMatch> run(final boolean allowEmptyLoopBody, final SyntaxNode statement) {
    return unitMatches(new NeedBracesCheck(allowEmptyLoopBody, MESSAGE), statement);
  }

  private static SyntaxNode block() {
    return n("block", n("blockStatement", t(IDENTIFIER, "x")));
  }

  private static SyntaxNode bareBody() {
    return n(STATEMENT, t(IDENTIFIER, "x"), t(SEMICOLON, ";"));
  }

  private static SyntaxNode emptyBody() {
    return n(STATEMENT, t(SEMICOLON, ";"));
  }
}
