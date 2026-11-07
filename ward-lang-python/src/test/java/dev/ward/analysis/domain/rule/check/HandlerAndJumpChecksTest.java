package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The checks on comparisons, handlers, raises and finally blocks, on hand-made trees. */
final class HandlerAndJumpChecksTest {

  private static final String VALUE_ERROR = "ValueError";

  @Test
  void comparisonOperatorReportsNotInAndNotIs() {
    assertThat(comparisonOp(ComparisonOperatorCheck.Problem.NOT_IN, Shapes.notTest("in")))
        .hasSize(1);
    assertThat(comparisonOp(ComparisonOperatorCheck.Problem.NOT_IS, Shapes.notTest("is")))
        .hasSize(1);
    assertThat(comparisonOp(ComparisonOperatorCheck.Problem.NOT_IN, Shapes.notTest("is")))
        .isEmpty();
  }

  @Test
  void comparisonOperatorReportsAnIdentityTestAgainstALiteral() {
    assertThat(
            comparisonOp(ComparisonOperatorCheck.Problem.IS_LITERAL, Nodes.comparison("is", "1")))
        .hasSize(1);
    assertThat(
            comparisonOp(
                ComparisonOperatorCheck.Problem.IS_LITERAL, Nodes.comparison("isnot", "\"a\"")))
        .hasSize(1);
    assertThat(
            comparisonOp(
                ComparisonOperatorCheck.Problem.IS_LITERAL, Nodes.comparison("is", "None")))
        .isEmpty();
    assertThat(
            comparisonOp(ComparisonOperatorCheck.Problem.IS_LITERAL, Nodes.comparison("==", "1")))
        .isEmpty();
  }

  @Test
  void comparisonOperatorUnwrapsNestedNotTests() {
    final SyntaxNode nested =
        Nodes.node(
            "not_test", Nodes.leaf("not"), Nodes.node("not_test", Nodes.comparison("in", "x")));

    assertThat(comparisonOp(ComparisonOperatorCheck.Problem.NOT_IN, nested)).hasSize(1);
  }

  @Test
  void exceptOrderReportsABareHandlerBeforeAnother() {
    assertThat(
            new ExceptOrderCheck("m")
                .matches(Nodes.tryWith(Nodes.clause(), Nodes.clause(VALUE_ERROR))))
        .hasSize(1);
    assertThat(
            new ExceptOrderCheck("m")
                .matches(Nodes.tryWith(Nodes.clause(VALUE_ERROR), Nodes.clause())))
        .isEmpty();
  }

  @Test
  void raiseWithoutFromReportsAChainedRaiseAndAcceptsBareOrFrom() {
    final SyntaxNode chained =
        Nodes.tryWith(Nodes.clause(VALUE_ERROR), Nodes.block(Nodes.raise("raise", "TypeError")));
    final SyntaxNode bare =
        Nodes.tryWith(Nodes.clause(VALUE_ERROR), Nodes.block(Nodes.raise("raise")));
    final SyntaxNode from =
        Nodes.tryWith(Nodes.clause(VALUE_ERROR), Nodes.block(Nodes.raise("raise", "from", "e")));

    assertThat(new RaiseWithoutFromCheck("m").matches(chained)).hasSize(1);
    assertThat(new RaiseWithoutFromCheck("m").matches(bare)).isEmpty();
    assertThat(new RaiseWithoutFromCheck("m").matches(from)).isEmpty();
  }

  @Test
  void duplicateHandlerReportsARepeatedTypeAndIgnoresAnAlias() {
    assertThat(
            new DuplicateHandlerCheck("m")
                .matches(Nodes.tryWith(Nodes.clause(VALUE_ERROR), Nodes.clause(VALUE_ERROR))))
        .hasSize(1);
    assertThat(
            new DuplicateHandlerCheck("m")
                .matches(Nodes.tryWith(Nodes.clause(VALUE_ERROR), Nodes.clause("TypeError"))))
        .isEmpty();
    assertThat(
            new DuplicateHandlerCheck("m")
                .matches(
                    Nodes.tryWith(Nodes.clause(VALUE_ERROR, "as", "e"), Nodes.clause(VALUE_ERROR))))
        .hasSize(1);
  }

  @Test
  void fStringDocstringReportsAFormattedFirstStatement() {
    final SyntaxNode formatted = Nodes.node("funcdef", Nodes.block(Nodes.leaf("f\"doc\"")));
    final SyntaxNode plain = Nodes.node("funcdef", Nodes.block(Nodes.leaf("\"doc\"")));

    assertThat(new FStringDocstringCheck("m").matches(formatted)).hasSize(1);
    assertThat(new FStringDocstringCheck("m").matches(plain)).isEmpty();
  }

  @Test
  void finallyJumpReportsAReturnButNotAJumpInsideANestedLoop() {
    final SyntaxNode returning =
        Nodes.node(
            "try_stmt",
            Nodes.leaf("finally"),
            Nodes.block(Nodes.node("return_stmt", Nodes.leaf("return"))));
    final SyntaxNode nestedLoop =
        Nodes.node(
            "try_stmt",
            Nodes.leaf("finally"),
            Nodes.block(
                Nodes.node(
                    "for_stmt", Nodes.block(Nodes.node("break_stmt", Nodes.leaf("break"))))));

    assertThat(new FinallyJumpCheck("m").matches(returning)).hasSize(1);
    assertThat(new FinallyJumpCheck("m").matches(nestedLoop)).isEmpty();
  }

  private static List<RuleMatch> comparisonOp(
      final ComparisonOperatorCheck.Problem problem, final SyntaxNode root) {
    return new ComparisonOperatorCheck(problem, "m").matches(root);
  }
}
