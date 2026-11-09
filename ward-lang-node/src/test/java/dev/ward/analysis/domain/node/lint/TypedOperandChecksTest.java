package dev.ward.analysis.domain.node.lint;

import static dev.ward.analysis.domain.node.lint.LintFixtures.positions;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The type-fact checks that read operands, templates, assertions and members. */
class TypedOperandChecksTest {

  private static final String MESSAGE = "message";

  private static final OperandPolicy ALL_ALLOWED =
      new OperandPolicy(true, true, true, true, true, false);

  private static final TemplatePolicy TEMPLATE_DEFAULTS =
      new TemplatePolicy(true, false, true, true, true, true, false, Set.of("Error"));

  @Test
  void unsafeAssignmentOfAnAnyValueIsReported() {
    assertThat(
            positions(
                new UnsafeAnyCheck(UnsafeAnyCheck.Site.ASSIGNMENT, MESSAGE),
                "declare const a: any;\nconst b: string = a;\n",
                new LintFixtures.Facts().typed("2:19", LintFixtures.named("any")).build()))
        .containsExactly("2:19");
  }

  @Test
  void unsafeCallMemberReturnArgumentAndNegationOfAnAnyValueAreReported() {
    final UnitTypeFacts call =
        new LintFixtures.Facts().typed("2:1", LintFixtures.named("any")).build();
    final UnitTypeFacts member =
        new LintFixtures.Facts().typed("2:11", LintFixtures.named("any")).build();
    final UnitTypeFacts ret =
        new LintFixtures.Facts().typed("3:10", LintFixtures.named("any")).build();
    final UnitTypeFacts argument =
        new LintFixtures.Facts().typed("2:10", LintFixtures.named("any")).build();

    assertThat(
            positions(
                new UnsafeAnyCheck(UnsafeAnyCheck.Site.CALL, MESSAGE),
                "declare const f: any;\nf();\n",
                call))
        .containsExactly("2:1");
    assertThat(
            positions(
                new UnsafeAnyCheck(UnsafeAnyCheck.Site.MEMBER, MESSAGE),
                "declare const o: any;\nconst x = o.name;\n",
                member))
        .containsExactly("2:11");
    assertThat(
            positions(
                new UnsafeAnyCheck(UnsafeAnyCheck.Site.RETURN, MESSAGE),
                "declare const v: any;\nfunction f(): string {\n  return v;\n}\n",
                ret))
        .containsExactly("3:10");
    assertThat(
            positions(
                new UnsafeAnyCheck(UnsafeAnyCheck.Site.ARGUMENT, MESSAGE),
                "declare const a: any;\nMath.abs(a);\n",
                argument))
        .containsExactly("2:10");
  }

  @Test
  void unaryMinusOfAValueThatIsNotNumericIsReported() {
    final String text = "declare const s: string;\nconst m = -s;\nconst k = -(s);\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("2:12", LintFixtures.primitive("string"))
            .typed("3:12", LintFixtures.primitive("string"))
            .build();

    assertThat(positions(new UnaryMinusCheck(MESSAGE), text, facts))
        .containsExactly("2:11", "3:11");
    assertThat(
            positions(
                new UnaryMinusCheck(MESSAGE),
                "declare const a: any;\nconst b = -a;\n",
                new LintFixtures.Facts().typed("2:12", LintFixtures.named("any")).build()))
        .isEmpty();
  }

  @Test
  void anAddOfAnObjectOperandIsAlwaysReported() {
    assertThat(
            positions(
                new RestrictOperandsCheck(ALL_ALLOWED, MESSAGE),
                "declare const o: { a: number };\nconst s = (o) + 1;\n",
                new LintFixtures.Facts()
                    .typed("2:11", LintFixtures.named("{ a: number; }"))
                    .build()))
        .containsExactly("2:11");
  }

  @Test
  void addOperandKindsAreReportedOnlyWhenTheirOptionForbidsThem() {
    final String text = "declare const flag: boolean;\nconst total = (flag) + 1;\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts().typed("2:15", LintFixtures.primitive("boolean")).build();

    assertThat(positions(new RestrictOperandsCheck(ALL_ALLOWED, MESSAGE), text, facts)).isEmpty();
    assertThat(
            positions(
                new RestrictOperandsCheck(
                    new OperandPolicy(true, false, true, true, true, false), MESSAGE),
                text,
                facts))
        .containsExactly("2:15");
  }

  @Test
  void numberAndStringMixingIsReportedWhenNotAllowed() {
    final String text =
        "declare const label: string;\ndeclare const count: number;\n"
            + "const text = (label) + (count);\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("3:14", LintFixtures.primitive("string"))
            .typed("3:24", LintFixtures.primitive("number"))
            .build();

    assertThat(positions(new RestrictOperandsCheck(ALL_ALLOWED, MESSAGE), text, facts)).isEmpty();
    assertThat(
            positions(
                new RestrictOperandsCheck(
                    new OperandPolicy(true, true, true, false, true, false), MESSAGE),
                text,
                facts))
        .containsExactly("3:14");
  }

  @Test
  void compoundAssignmentIsCheckedUnlessItIsSkipped() {
    final String text = "declare let n: number;\ndeclare const s: string;\nn += (s);\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("3:1", LintFixtures.primitive("number"))
            .typed("3:6", LintFixtures.primitive("string"))
            .build();

    assertThat(
            positions(
                new RestrictOperandsCheck(
                    new OperandPolicy(true, true, true, false, true, false), MESSAGE),
                text,
                facts))
        .containsExactly("3:1");
    assertThat(
            positions(
                new RestrictOperandsCheck(
                    new OperandPolicy(true, true, true, false, true, true), MESSAGE),
                text,
                facts))
        .isEmpty();
  }

  @Test
  void aTemplateOfAnArrayIsReportedUnlessArraysAreAllowed() {
    final String text =
        "declare const items: string[];\ndeclare const shape: { a: number };\n"
            + "const text = `${items}${shape}`;\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("3:17", new TypeRef("string", List.of(), 1, true))
            .typed("3:25", LintFixtures.named("{ a: number; }"))
            .build();

    assertThat(positions(new RestrictTemplateCheck(TEMPLATE_DEFAULTS, MESSAGE), text, facts))
        .containsExactly("3:17", "3:25");
    assertThat(
            positions(
                new RestrictTemplateCheck(
                    new TemplatePolicy(true, true, true, true, true, true, false, Set.of("Error")),
                    MESSAGE),
                text,
                facts))
        .containsExactly("3:25");
  }

  @Test
  void aListedErrorTypeIsNotReportedInATemplate() {
    final String text = "declare const e: Error;\nconst t = `${e}`;\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts().typed("2:14", LintFixtures.named("Error")).build();

    assertThat(positions(new RestrictTemplateCheck(TEMPLATE_DEFAULTS, MESSAGE), text, facts))
        .isEmpty();
    assertThat(
            positions(
                new RestrictTemplateCheck(
                    new TemplatePolicy(true, false, true, true, true, true, false, Set.of()),
                    MESSAGE),
                text,
                facts))
        .containsExactly("2:14");
  }
}
