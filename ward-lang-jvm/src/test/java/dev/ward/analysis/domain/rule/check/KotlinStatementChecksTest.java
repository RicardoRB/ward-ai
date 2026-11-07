package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.group;
import static dev.ward.analysis.domain.rule.check.TokenTrees.tok;
import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** Runs the Kotlin statement and range checks over hand-made token trees. */
class KotlinStatementChecksTest {

  private static final String STATEMENTS = "statements";
  private static final String STATEMENT = "statement";
  private static final String PREFIX = "prefixUnaryExpression";
  private static final String RANGE = "rangeExpression";
  private static final String INFIX = "infixFunctionCall";
  private static final String TOKEN = "token";
  private static final String MESSAGE = "m";
  private static final int SECOND_LINE = 2;
  private static final int OFFSET = 10;
  private static final int LATER_OFFSET = 20;
  private static final int NAME_LENGTH = 3;

  private static SyntaxNode jump(final int line, final int offset) {
    return group(STATEMENT, group("jumpExpression", tok(TOKEN, "return", line, offset)));
  }

  private static SyntaxNode call(final int line, final int offset) {
    return group(
        STATEMENT, tok(TOKEN, "run", line, offset), tok(TOKEN, "()", line, offset + NAME_LENGTH));
  }

  private static SyntaxNode signed(final String sign, final int line, final int offset) {
    return group(
        STATEMENT,
        group(PREFIX, tok(TOKEN, sign, line, offset), tok(TOKEN, "3", line, offset + 2)));
  }

  private static SyntaxNode range(
      final String kind, final String from, final String operator, final String to) {
    return group(
        kind,
        tok(TOKEN, from, 1, 0),
        tok(TOKEN, operator, 1, OFFSET),
        tok(TOKEN, to, 1, LATER_OFFSET));
  }

  @Test
  void reportsTheStatementAfterAJump() {
    final SyntaxNode list = group(STATEMENTS, jump(1, 0), call(SECOND_LINE, OFFSET));

    assertThat(unitMatches(new UnreachableStatementCheck(MESSAGE), list)).hasSize(1);
  }

  @Test
  void acceptsAJumpThatEndsTheBlockAndStatementsWithoutJump() {
    final UnreachableStatementCheck check = new UnreachableStatementCheck(MESSAGE);

    assertThat(unitMatches(check, group(STATEMENTS, call(1, 0), jump(SECOND_LINE, OFFSET))))
        .isEmpty();
    assertThat(unitMatches(check, group(STATEMENTS, call(1, 0), call(SECOND_LINE, OFFSET))))
        .isEmpty();
  }

  @Test
  void reportsASignedStatementThatIsNotLast() {
    final SyntaxNode list = group(STATEMENTS, signed("+", 1, 0), call(SECOND_LINE, OFFSET));

    assertThat(unitMatches(new UnusedUnaryOperatorCheck(MESSAGE), list)).hasSize(1);
    assertThat(
            unitMatches(
                new UnusedUnaryOperatorCheck(MESSAGE),
                group(STATEMENTS, signed("-", 1, 0), call(SECOND_LINE, OFFSET))))
        .hasSize(1);
  }

  @Test
  void acceptsTheLastStatementIncrementsAndOtherStatements() {
    final UnusedUnaryOperatorCheck check = new UnusedUnaryOperatorCheck(MESSAGE);

    assertThat(unitMatches(check, group(STATEMENTS, call(1, 0), signed("-", SECOND_LINE, OFFSET))))
        .isEmpty();
    assertThat(unitMatches(check, group(STATEMENTS, signed("++", 1, 0), call(SECOND_LINE, OFFSET))))
        .isEmpty();
    assertThat(unitMatches(check, group(STATEMENTS, signed("--", 1, 0), call(SECOND_LINE, OFFSET))))
        .isEmpty();
    assertThat(unitMatches(check, group(STATEMENTS, call(1, 0), call(SECOND_LINE, OFFSET))))
        .isEmpty();
  }

  @Test
  void reportsRangesThatAreAlwaysEmpty() {
    final InvalidRangeCheck check = new InvalidRangeCheck(MESSAGE);

    assertThat(unitMatches(check, range(RANGE, "10", "..", "1"))).hasSize(1);
    assertThat(unitMatches(check, range(INFIX, "5", "until", "1"))).hasSize(1);
    assertThat(unitMatches(check, range(INFIX, "1", "downTo", "5"))).hasSize(1);
  }

  @Test
  void acceptsRangesWithElementsAndNodesOfTheWrongKind() {
    final InvalidRangeCheck check = new InvalidRangeCheck(MESSAGE);

    assertThat(unitMatches(check, range(RANGE, "1", "..", "10"))).isEmpty();
    assertThat(unitMatches(check, range(INFIX, "0", "until", "5"))).isEmpty();
    assertThat(unitMatches(check, range(INFIX, "5", "downTo", "1"))).isEmpty();
    assertThat(unitMatches(check, range(RANGE, "1", "downTo", "5"))).isEmpty();
    assertThat(unitMatches(check, range(INFIX, "a", "until", "5"))).isEmpty();
  }
}
