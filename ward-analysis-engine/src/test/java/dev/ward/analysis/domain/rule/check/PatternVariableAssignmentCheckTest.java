package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The pattern-variable-assignment check: names an instanceof pattern declares, and assignments. */
class PatternVariableAssignmentCheckTest extends TreeBuilders {

  private static final String MESSAGE = "pattern {value}";

  @Test
  void assignmentToAPatternVariableIsReported() {
    final SyntaxNode method = method(assignment("text", "other"), instanceOf("String", "text"));
    assertThat(new PatternVariableAssignmentCheck(MESSAGE).check(unit(method), FILE)).hasSize(1);
  }

  @Test
  void assignmentToAnotherNameOrWithoutAPatternIsNotReported() {
    final SyntaxNode other = method(assignment("name", "other"), instanceOf("String", "text"));
    assertThat(new PatternVariableAssignmentCheck(MESSAGE).check(unit(other), FILE)).isEmpty();
    final SyntaxNode none = method(assignment("text", "other"), n(EXPRESSION, id("value")));
    assertThat(new PatternVariableAssignmentCheck(MESSAGE).check(unit(none), FILE)).isEmpty();
  }

  private static SyntaxNode method(final SyntaxNode... statements) {
    final SyntaxNode[] wrapped = new SyntaxNode[statements.length];
    for (int index = 0; index < statements.length; index++) {
      wrapped[index] = n(BLOCK_STATEMENT, n(STATEMENT, statements[index]));
    }
    return n(METHOD_DECLARATION, n(BLOCK, wrapped));
  }

  private static SyntaxNode instanceOf(final String type, final String name) {
    return n(
        EXPRESSION,
        n(EXPRESSION, id("value")),
        t("'instanceof'", "instanceof"),
        n("typeType", t(TOKEN_IDENTIFIER, type), t(TOKEN_IDENTIFIER, name)));
  }

  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String STATEMENT = "statement";
}
