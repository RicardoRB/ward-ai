package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The consecutive-appends check: two appends to the same builder on adjacent statements. */
class ConsecutiveAppendsCheckTest extends TreeBuilders {

  private static final String LITERAL = "STRING_LITERAL";
  private static final String BUF = "buf";

  private final ConsecutiveAppendsCheck check = new ConsecutiveAppendsCheck(MESSAGE);

  @Test
  void twoAppendsToTheSameBuilderOnAdjacentStatementsAreReported() {
    final SyntaxNode block = block(append(BUF, "\"Hello\""), append(BUF, "foo"));

    assertThat(check.check(unit(block), FILE)).hasSize(1);
  }

  @Test
  void anAppendFollowedByAnAppendToAnotherBuilderIsNotReported() {
    final SyntaxNode block = block(append(BUF, "\"Hello\""), append("other", "foo"));

    assertThat(check.check(unit(block), FILE)).isEmpty();
  }

  @Test
  void aChainedAppendIsNotReported() {
    final SyntaxNode chain =
        n(
            STATEMENT,
            id(BUF),
            t(TOKEN_DOT, "."),
            id("append"),
            t(TOKEN_OPEN, "("),
            t(LITERAL, "\"Hello\""),
            t(TOKEN_CLOSE, ")"),
            t(TOKEN_DOT, "."),
            id("append"),
            t(TOKEN_OPEN, "("),
            t(TOKEN_IDENTIFIER, "foo"),
            t(TOKEN_CLOSE, ")"),
            t(TOKEN_SEMICOLON, ";"));

    assertThat(check.check(unit(block(chain, append(BUF, "\"World\""))), FILE)).isEmpty();
  }

  @Test
  void threeAppendsReportTheFirstTwo() {
    final SyntaxNode block = block(append(BUF, "\"a\""), append(BUF, "b"), append(BUF, "\"c\""));

    assertThat(check.check(unit(block), FILE)).hasSize(2);
  }

  private static SyntaxNode block(final SyntaxNode... statements) {
    final SyntaxNode[] children = new SyntaxNode[statements.length + 2];
    children[0] = t("'{'", "{");
    System.arraycopy(statements, 0, children, 1, statements.length);
    children[children.length - 1] = t("'}'", "}");
    return n(BLOCK, children);
  }

  private static SyntaxNode append(final String receiver, final String argument) {
    final String kind = argument.startsWith("\"") ? LITERAL : TOKEN_IDENTIFIER;
    return n(
        STATEMENT,
        id(receiver),
        t(TOKEN_DOT, "."),
        id("append"),
        t(TOKEN_OPEN, "("),
        t(kind, argument),
        t(TOKEN_CLOSE, ")"),
        t(TOKEN_SEMICOLON, ";"));
  }
}
