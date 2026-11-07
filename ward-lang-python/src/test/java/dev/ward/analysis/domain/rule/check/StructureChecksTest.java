package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import org.junit.jupiter.api.Test;

/** The checks on class bodies, set displays, loop targets, module order and format operands. */
final class StructureChecksTest {

  private static final String PASS = "pass";
  private static final String ABC = "ABC";

  @Test
  void emptyAbstractMethodReportsAPassingMethodOfAnAbcClass() {
    assertThat(
            new EmptyAbstractMethodCheck("m")
                .matches(Shapes.classWith(ABC, Shapes.method(Nodes.block(Nodes.leaf(PASS))))))
        .hasSize(1);
  }

  @Test
  void emptyAbstractMethodIgnoresDecoratedMethodsOtherBasesAndNonEmptyBodies() {
    final SyntaxNode decorated =
        Shapes.classWith(
            ABC, Nodes.node("decorated", Shapes.method(Nodes.block(Nodes.leaf(PASS)))));

    assertThat(new EmptyAbstractMethodCheck("m").matches(decorated)).isEmpty();
    assertThat(
            new EmptyAbstractMethodCheck("m")
                .matches(Shapes.classWith("Base", Shapes.method(Nodes.block(Nodes.leaf(PASS))))))
        .isEmpty();
    assertThat(
            new EmptyAbstractMethodCheck("m")
                .matches(Shapes.classWith(ABC, Shapes.method(Nodes.block(Nodes.leaf("x"))))))
        .isEmpty();
  }

  @Test
  void setDuplicatesReportsEachRepeatedLiteralAndIgnoresDictsAndComprehensions() {
    assertThat(new SetDuplicatesCheck("m").matches(Shapes.atom("{1,1,\"a\",\"a\"}"))).hasSize(2);
    assertThat(new SetDuplicatesCheck("m").matches(Shapes.atom("{1,2}"))).isEmpty();
    assertThat(new SetDuplicatesCheck("m").matches(Shapes.atom("{1:2}"))).isEmpty();
    assertThat(new SetDuplicatesCheck("m").matches(Shapes.atom("{x for x in y}"))).isEmpty();
    assertThat(new SetDuplicatesCheck("m").matches(Shapes.atom("{a,a}"))).isEmpty();
  }

  @Test
  void loopVariableReportsAnUnusedTargetAndAReuseOfTheIterable() {
    final LoopVariableCheck unused = new LoopVariableCheck(LoopVariableCheck.Problem.UNUSED, "m");
    final LoopVariableCheck overrides =
        new LoopVariableCheck(LoopVariableCheck.Problem.OVERRIDES_ITERATOR, "m");

    assertThat(unused.matches(Shapes.forLoop("i", "range", Nodes.block(Nodes.leaf("print")))))
        .hasSize(1);
    assertThat(
            overrides.matches(
                Shapes.forLoop("x", "x", Nodes.block(Nodes.leaf("print"), Nodes.leaf("x")))))
        .hasSize(1);
    assertThat(unused.matches(Shapes.forLoop("_", "range", Nodes.block(Nodes.leaf("1")))))
        .isEmpty();
    assertThat(overrides.matches(Shapes.forLoop("i", "range", Nodes.block(Nodes.leaf("print")))))
        .isEmpty();
  }

  @Test
  void loopVariableReportsATargetNamedLikeAModuleImport() {
    final SyntaxNode module =
        Shapes.file(
            Shapes.importStatement("os"),
            Nodes.statement(Shapes.forLoop("os", "range", Nodes.block(Nodes.leaf("1")))));

    assertThat(new LoopVariableCheck(LoopVariableCheck.Problem.SHADOWS_IMPORT, "m").matches(module))
        .hasSize(1);
  }

  @Test
  void moduleOrderReportsAnImportAfterCodeAndAFutureImportAfterAStatement() {
    final SyntaxNode importAfterCode =
        Shapes.file(Nodes.statement(Nodes.leaf("x")), Shapes.importStatement("os"));
    final SyntaxNode lateFuture = Shapes.file(Nodes.statement(Nodes.leaf("x")), Shapes.future());

    assertThat(
            new ModuleOrderCheck(ModuleOrderCheck.Problem.IMPORT_AFTER_CODE, "m")
                .matches(importAfterCode))
        .hasSize(1);
    assertThat(
            new ModuleOrderCheck(ModuleOrderCheck.Problem.FUTURE_AFTER_STATEMENT, "m")
                .matches(lateFuture))
        .hasSize(1);
  }

  @Test
  void moduleOrderAcceptsADocstringBeforeImportsAndFutures() {
    final SyntaxNode ok =
        Shapes.file(
            Nodes.statement(Nodes.leaf("\"doc\"")), Shapes.future(), Shapes.importStatement("os"));

    assertThat(new ModuleOrderCheck(ModuleOrderCheck.Problem.IMPORT_AFTER_CODE, "m").matches(ok))
        .isEmpty();
    assertThat(
            new ModuleOrderCheck(ModuleOrderCheck.Problem.FUTURE_AFTER_STATEMENT, "m").matches(ok))
        .isEmpty();
  }

  @Test
  void percentFormatReportsAnExpectedSequenceForSeveralPositionals() {
    final SyntaxNode operation =
        Nodes.node("expr", Nodes.leaf("\"%s %s\""), Nodes.leaf("%"), Nodes.leaf("{\"x\":1}"));

    assertThat(
            new PercentFormatCheck(PercentFormatCheck.Problem.EXPECTED_SEQUENCE, "m")
                .matches(operation))
        .hasSize(1);
  }
}
