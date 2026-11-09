package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.TokenTrees.group;
import static dev.ward.analysis.domain.rule.check.TokenTrees.tok;
import static dev.ward.analysis.domain.rule.check.TokenTrees.unitMatches;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Runs the Kotlin complex-condition check over hand-made condition trees. */
class KotlinComplexConditionCheckTest {

  private static final String MESSAGE = "m";
  private static final String IF = "ifExpression";
  private static final String OPERATOR = "'&&'";
  private static final int LINE = 1;
  private static final int OFFSET = 0;
  private static final int THRESHOLD = 4;

  private static SyntaxNode condition(final int operators) {
    final SyntaxNode[] parts = new SyntaxNode[2 * operators + 1];
    for (int i = 0; i < parts.length; i++) {
      parts[i] = i % 2 == 0 ? tok("token", "a", LINE, OFFSET) : tok(OPERATOR, "&&", LINE, OFFSET);
    }
    return group("expression", parts);
  }

  @Test
  void reportsAConditionAtItsThresholdOnly() {
    final SyntaxNode statement =
        group(IF, tok("'if'", "if", LINE, OFFSET), condition(THRESHOLD - 1));

    assertThat(
            unitMatches(new KotlinComplexConditionCheck(Set.of(IF), THRESHOLD, MESSAGE), statement))
        .hasSize(1);
    assertThat(
            unitMatches(
                new KotlinComplexConditionCheck(Set.of(IF), THRESHOLD + 1, MESSAGE), statement))
        .isEmpty();
    final SyntaxNode twoOperators = group(IF, tok("'if'", "if", LINE, OFFSET), condition(2));
    assertThat(
            unitMatches(
                new KotlinComplexConditionCheck(Set.of(IF), THRESHOLD, MESSAGE), twoOperators))
        .isEmpty();
  }
}
