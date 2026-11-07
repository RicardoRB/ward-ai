package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The parameter-assignment check on small methods: plain-name targets, parameters and scope. */
class ParameterAssignmentCheckTest extends TreeBuilders {

  private static final String MESSAGE = "assigns {value}";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String STATEMENT = "statement";
  private static final String VOID_TYPE = "void";

  @Test
  void assignmentToAParameterIsReportedAtItsOperator() {
    final SyntaxNode method = method("size", assignment("size", "other"));
    assertThat(Trees.messages(new ParameterAssignmentCheck(MESSAGE).check(unit(method), FILE)))
        .containsExactly("assigns size");
  }

  @Test
  void assignmentToALocalOrAFieldIsNotReported() {
    final SyntaxNode method = method("size", assignment("other", "size"));
    assertThat(new ParameterAssignmentCheck(MESSAGE).check(unit(method), FILE)).isEmpty();
    final SyntaxNode dotted = method("size", assignment("this.size", "other"));
    assertThat(new ParameterAssignmentCheck(MESSAGE).check(unit(dotted), FILE)).isEmpty();
  }

  @Test
  void assignmentOutsideAnyMethodIsNotReported() {
    final SyntaxNode field = n(EXPRESSION, assignment("size", "other"));
    assertThat(new ParameterAssignmentCheck(MESSAGE).check(unit(field), FILE)).isEmpty();
  }

  private static SyntaxNode method(final String parameter, final SyntaxNode assignment) {
    return n(
        METHOD_DECLARATION,
        t(VOID_TYPE, VOID_TYPE),
        t(TOKEN_IDENTIFIER, "run"),
        n(
            FORMAL_PARAMETERS,
            n(FORMAL_PARAMETER, t("int", "int"), n(PARAMETER_ID, t(TOKEN_IDENTIFIER, parameter)))),
        n(BLOCK, n(BLOCK_STATEMENT, n(STATEMENT, assignment))));
  }
}
