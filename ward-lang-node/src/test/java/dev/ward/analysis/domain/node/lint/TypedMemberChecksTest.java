package dev.ward.analysis.domain.node.lint;

import static dev.ward.analysis.domain.node.lint.LintFixtures.positions;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.FieldRef;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The type-fact checks that read members, object conversions and enum comparisons. */
class TypedMemberChecksTest {

  private static final String MESSAGE = "message";

  private static final TemplatePolicy TEMPLATE_DEFAULTS =
      new TemplatePolicy(true, false, true, true, true, true, false, Set.of("Error"));

  @Test
  void aNeverPlaceholderIsReportedUnlessNeverIsAllowed() {
    final String text = "declare const n: never;\nconst t = `${n}`;\n";
    final UnitTypeFacts facts =
        new LintFixtures.Facts().typed("2:14", LintFixtures.primitive("never")).build();

    assertThat(positions(new RestrictTemplateCheck(TEMPLATE_DEFAULTS, MESSAGE), text, facts))
        .containsExactly("2:14");
    assertThat(
            positions(
                new RestrictTemplateCheck(
                    new TemplatePolicy(true, false, true, true, true, true, true, Set.of()),
                    MESSAGE),
                text,
                facts))
        .isEmpty();
  }

  @Test
  void assertingTheTypeAnExpressionAlreadyHasIsReported() {
    final String text = "declare const n: number;\nconst m = (n) as number;\n";

    assertThat(
            positions(
                new UnnecessaryAssertionCheck(MESSAGE),
                text,
                new LintFixtures.Facts().typed("2:11", LintFixtures.primitive("number")).build()))
        .containsExactly("2:11");
    assertThat(
            positions(
                new UnnecessaryAssertionCheck(MESSAGE),
                text,
                new LintFixtures.Facts().typed("2:11", LintFixtures.primitive("string")).build()))
        .isEmpty();
  }

  @Test
  void aMethodReadWithoutACallIsReportedAndAFieldIsNot() {
    final String text =
        "declare const counter: { label: string; reset(): void };\n"
            + "const bound = counter.reset;\ncounter.reset();\nconst v = counter.label;\n";
    final TypeMembers members =
        new TypeMembers(
            List.of(LintFixtures.call("reset", LintFixtures.named("void"))),
            List.of(new FieldRef("label", LintFixtures.named("string"), false, List.of())),
            List.of(),
            List.of());
    final UnitTypeFacts facts =
        new LintFixtures.Facts().membered("2:15", members).membered("4:11", members).build();

    assertThat(positions(new UnboundMethodCheck(MESSAGE), text, facts)).containsExactly("2:15");
  }

  @Test
  void anObjectWithoutAToStringIsReportedInATemplate() {
    final String text =
        "declare const point: { x: number };\ndeclare const tagged: { toString(): string };\n"
            + "const a = `${point}`;\nconst b = `${tagged}`;\n";
    final TypeMembers bare =
        new TypeMembers(
            List.of(),
            List.of(new FieldRef("x", LintFixtures.named("number"), false, List.of())),
            List.of(),
            List.of());
    final TypeMembers tagged =
        new TypeMembers(
            List.of(LintFixtures.call("toString", LintFixtures.named("string"))),
            List.of(),
            List.of(),
            List.of());
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("3:14", LintFixtures.named("{ x: number; }"))
            .membered("3:14", bare)
            .typed("4:14", LintFixtures.named("{ toString(): string; }"))
            .membered("4:14", tagged)
            .build();

    assertThat(positions(new BaseToStringCheck(MESSAGE), text, facts)).containsExactly("3:14");
  }

  @Test
  void comparingAnEnumWithAPrimitiveIsReported() {
    final String text =
        "enum Color { Red, Green }\ndeclare const color: Color;\nif (color === 1) {\n}\n";
    final TypeShape enumShape = new TypeShape(TypeShape.Kind.ENUM, false, false, false, List.of());
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("3:5", LintFixtures.named("Color"))
            .shaped("3:5", enumShape)
            .typed("3:15", LintFixtures.primitive("1"))
            .build();

    assertThat(positions(new EnumComparisonCheck(MESSAGE), text, facts)).containsExactly("3:5");
  }

  @Test
  void stringConversionsOfAnObjectWithoutAToStringAreReported() {
    final String text =
        "declare const point: { x: number };\nconst label = String(point);\n"
            + "const same = point.toString();\n";
    final TypeMembers bare =
        new TypeMembers(
            List.of(),
            List.of(new FieldRef("x", LintFixtures.named("number"), false, List.of())),
            List.of(),
            List.of());
    final UnitTypeFacts facts =
        new LintFixtures.Facts()
            .typed("2:22", LintFixtures.named("{ x: number; }"))
            .membered("2:22", bare)
            .typed("3:14", LintFixtures.named("{ x: number; }"))
            .membered("3:14", bare)
            .build();

    assertThat(positions(new BaseToStringCheck(MESSAGE), text, facts))
        .containsExactly("2:22", "3:14");
  }
}
