package dev.ward.analysis.domain.node.lint;

import static dev.ward.analysis.domain.node.lint.LintFixtures.call;
import static dev.ward.analysis.domain.node.lint.LintFixtures.named;
import static dev.ward.analysis.domain.node.lint.LintFixtures.positions;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

class MisusedPromiseCheckTest {

  private static final String MESSAGE = "promise";

  private static final String CONDITION =
      "async function ok(): Promise<boolean> {\n  return true;\n}\nif (ok()) {\n}\n";
  private static final String SPREAD = "const bag = { ...ok() };\n";
  private static final String VOID_CALL =
      "declare const api: { schedule(task: () => void): void };\n"
          + "declare const job: () => Promise<void>;\nvoid api.schedule(job);\n";

  private static UnitTypeFacts conditionFacts() {
    return new LintFixtures.Facts().typed("4:5", named("Promise<boolean>")).build();
  }

  private static UnitTypeFacts spreadFacts() {
    return new LintFixtures.Facts().typed("1:18", named("Promise<boolean>")).build();
  }

  @Test
  void reportsAPromiseCondition() {
    assertThat(
            positions(
                new MisusedPromiseCheck(true, false, false, MESSAGE), CONDITION, conditionFacts()))
        .containsExactly("4:5");
  }

  @Test
  void reportsAPromiseSpreadInAnObjectLiteral() {
    assertThat(
            positions(new MisusedPromiseCheck(false, true, false, MESSAGE), SPREAD, spreadFacts()))
        .containsExactly("1:18");
  }

  @Test
  void reportsAPromiseFunctionPassedWhereAVoidReturnIsExpected() {
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .called("3:6", call("schedule", named("void"), named("() => void")))
            .typed("3:19", named("() => Promise<void>"))
            .build();

    assertThat(positions(new MisusedPromiseCheck(false, false, true, MESSAGE), VOID_CALL, facts))
        .containsExactly("3:19");
  }

  @Test
  void skipsEachCheckWhenItsOptionIsOff() {
    assertThat(
            positions(
                new MisusedPromiseCheck(false, true, false, MESSAGE), CONDITION, conditionFacts()))
        .isEmpty();
    assertThat(
            positions(new MisusedPromiseCheck(true, false, false, MESSAGE), SPREAD, spreadFacts()))
        .isEmpty();
  }

  @Test
  void leavesUnknownTypesAlone() {
    assertThat(
            positions(
                new MisusedPromiseCheck(true, true, true, MESSAGE),
                CONDITION + SPREAD,
                UnitTypeFacts.none()))
        .isEqualTo(List.of());
  }

  @Test
  void aPromiseStoredInAVariableDeclaredAsReturningVoidIsReported() {
    final String text =
        "const task: () => void = async () => {};\nconst other: () => number = async () => 1;\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("1:7", named("() => void"))
            .typed("1:26", named("() => Promise<void>"))
            .typed("2:7", named("() => number"))
            .typed("2:27", named("() => Promise<number>"))
            .build();

    assertThat(positions(new MisusedPromiseCheck(false, false, true, MESSAGE), text, facts))
        .containsExactly("1:26");
  }
}
