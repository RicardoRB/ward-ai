package dev.ward.analysis.domain.node.lint;

import static dev.ward.analysis.domain.node.lint.LintFixtures.positions;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The checks that read the native type facts, each with facts that the fixtures also use. */
class TypedChecksTest {

  private static final String MESSAGE = "message";

  @Test
  void awaitOfAPrimitiveIsReported() {
    assertThat(
            positions(
                new AwaitThenableCheck(false, MESSAGE),
                "async function f(n: number) {\n  await (n);\n}\n",
                new LintFixtures.Facts().typed("2:9", LintFixtures.primitive("number")).build()))
        .containsExactly("2:3");
  }

  @Test
  void primitivesInAPromiseAggregatorAreReportedUnlessAMixedArrayIsAllowed() {
    final String text =
        "declare const p: Promise<number>;\ndeclare const n: number;\n"
            + "void Promise.all([n, p]);\nvoid Promise.all([n, n]);\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("3:19", LintFixtures.primitive("number"))
            .typed("3:22", LintFixtures.named("Promise<number>"))
            .typed("4:19", LintFixtures.primitive("number"))
            .typed("4:22", LintFixtures.primitive("number"))
            .build();

    assertThat(positions(new AwaitThenableCheck(false, MESSAGE), text, facts))
        .containsExactly("3:19", "4:19", "4:22");
    assertThat(positions(new AwaitThenableCheck(true, MESSAGE), text, facts))
        .containsExactly("4:19", "4:22");
  }

  @Test
  void forInOverAnArrayIsReportedAndOverAnObjectIsNot() {
    final String text =
        "const xs: string[] = [];\nconst o = {};\nfor (const i in xs) {\n}\n"
            + "for (const k in o) {\n}\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("3:17", new TypeRef("string", List.of(), 1, true))
            .typed("5:17", LintFixtures.named("{}"))
            .build();

    assertThat(positions(new NoForInArrayCheck(MESSAGE), text, facts)).containsExactly("3:1");
  }

  @Test
  void deleteOfAnArrayElementIsReported() {
    final String text = "const xs: number[] = [1];\ndelete (xs)[0];\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts().typed("2:8", new TypeRef("number", List.of(), 1, true)).build();

    assertThat(positions(new NoArrayDeleteCheck(MESSAGE), text, facts)).containsExactly("2:1");
  }

  @Test
  void aFloatingPromiseIsReportedUnlessItIsVoidedHandledOrAnIife() {
    final String text =
        "async function run(): Promise<void> {}\nrun();\nawait run();\n"
            + "run().catch(() => {});\nvoid run();\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("2:1", LintFixtures.named("Promise<void>"))
            .typed("4:1", LintFixtures.named("Promise<void>"))
            .typed("5:6", LintFixtures.named("Promise<void>"))
            .build();

    assertThat(positions(new FloatingPromiseCheck(true, false, MESSAGE), text, facts))
        .containsExactly("2:1");
    assertThat(positions(new FloatingPromiseCheck(false, false, MESSAGE), text, facts))
        .containsExactly("2:1", "5:6");
  }

  @Test
  void anIifeIsReportedOnlyWhenTheOptionDoesNotIgnoreIt() {
    final UnitTypeFacts facts =
        new LintFixtures.Facts().typed("1:1", LintFixtures.named("Promise<void>")).build();
    final String text = "(async () => {})();\n";

    assertThat(positions(new FloatingPromiseCheck(true, false, MESSAGE), text, facts))
        .containsExactly("1:1");
    assertThat(positions(new FloatingPromiseCheck(true, true, MESSAGE), text, facts)).isEmpty();
  }

  @Test
  void throwingAValueThatIsNotAnErrorIsReported() {
    final String text = "throw 'oops';\nthrow new Error('x');\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("1:7", LintFixtures.primitive("\"oops\""))
            .typed("2:7", LintFixtures.named("Error"))
            .build();

    assertThat(positions(new OnlyThrowErrorCheck(true, true, true, Set.of(), MESSAGE), text, facts))
        .containsExactly("1:7");
  }

  @Test
  void throwingAnyOrUnknownIsReportedOnlyWhenTheOptionsForbidIt() {
    final String text = "declare const a: any;\nthrow a;\ndeclare const u: unknown;\nthrow u;\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("2:7", LintFixtures.named("any"))
            .typed("4:7", LintFixtures.named("unknown"))
            .build();

    assertThat(positions(new OnlyThrowErrorCheck(true, true, true, Set.of(), MESSAGE), text, facts))
        .isEmpty();
    assertThat(
            positions(new OnlyThrowErrorCheck(true, false, true, Set.of(), MESSAGE), text, facts))
        .containsExactly("2:7");
    assertThat(
            positions(new OnlyThrowErrorCheck(true, true, false, Set.of(), MESSAGE), text, facts))
        .containsExactly("4:7");
  }

  @Test
  void aListedTypeIsNotReportedAsAThrownValue() {
    final String text =
        "declare const ticket: Ticket;\ndeclare const text: string;\n"
            + "throw ticket;\nthrow text;\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("3:7", LintFixtures.named("Ticket"))
            .typed("4:7", LintFixtures.primitive("string"))
            .build();

    assertThat(
            positions(
                new OnlyThrowErrorCheck(true, true, true, Set.of("Ticket"), MESSAGE), text, facts))
        .containsExactly("4:7");
  }

  @Test
  void aRethrownCatchParameterIsReportedOnlyWhenRethrowingIsNotAllowed() {
    final String text = "try {\n  run();\n} catch (err) {\n  throw err;\n}\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts().typed("4:9", LintFixtures.primitive("string")).build();

    assertThat(positions(new OnlyThrowErrorCheck(true, true, true, Set.of(), MESSAGE), text, facts))
        .isEmpty();
    assertThat(
            positions(new OnlyThrowErrorCheck(false, true, true, Set.of(), MESSAGE), text, facts))
        .containsExactly("4:9");
  }
}
