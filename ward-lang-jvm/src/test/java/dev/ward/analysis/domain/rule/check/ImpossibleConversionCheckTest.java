package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImpossibleConversionCheckTest {

  private static final String EXPRESSION = "expression";
  private static final String TYPE_TYPE = "typeType";
  private static final String TEXT = "\"text\"";
  private static final String HOLD = "    Object v = \"text\";\n";
  private static final TypeRef STRING = TypeRef.of("java.lang.String");
  private static final TypeRef INTEGER = TypeRef.of("java.lang.Integer");
  private static final TypeRef BASE = TypeRef.of("app.Base");
  private static final TypeRef CHILD = TypeRef.of("app.Child");
  private static final TypeRef CHARS = TypeRef.of("java.lang.CharSequence");
  private static final TypeRef NUMBER = TypeRef.of("java.lang.Number");
  private static final SpanFacts STRING_VALUE =
      ParsedFacts.typed(STRING, TypeShape.Kind.CLASS, true, CHARS, ParsedFacts.OBJECT);
  private static final SpanFacts INTEGER_TARGET =
      ParsedFacts.typed(INTEGER, TypeShape.Kind.CLASS, true, NUMBER, ParsedFacts.OBJECT);
  private static final SpanFacts CHARS_TARGET =
      ParsedFacts.typed(CHARS, TypeShape.Kind.INTERFACE, false, ParsedFacts.OBJECT);
  private static final SpanFacts BASE_VALUE =
      ParsedFacts.typed(BASE, TypeShape.Kind.CLASS, false, ParsedFacts.OBJECT);
  private static final SpanFacts CHILD_TARGET =
      ParsedFacts.typed(CHILD, TypeShape.Kind.CLASS, false, BASE, ParsedFacts.OBJECT);

  private static List<String> run(
      final ImpossibleConversionCheck.Mode mode, final String statements, final SpanFacts target) {
    return new ParsedFacts(
            "final class C {\n  Object t(boolean c, Object p) {\n" + statements + "  }\n}\n")
        .at(EXPRESSION, TEXT, STRING_VALUE)
        .at(EXPRESSION, "newBase()", BASE_VALUE)
        .at(EXPRESSION, "make()", BASE_VALUE)
        .at(TYPE_TYPE, "Integer", target)
        .at(TYPE_TYPE, "CharSequence", target)
        .at(TYPE_TYPE, "Child", target)
        .run(new ImpossibleConversionCheck(mode, "message"));
  }

  private static List<String> cast(final String statements, final SpanFacts target) {
    return run(ImpossibleConversionCheck.Mode.CAST, statements, target);
  }

  @Test
  void aCastOfWhatAVariableHoldsToAnUnrelatedTypeIsReported() {
    final ImpossibleConversionCheck check =
        new ImpossibleConversionCheck(ImpossibleConversionCheck.Mode.CAST, "message");

    assertThat(check.needs().facts())
        .containsExactlyInAnyOrder(FactKind.TYPE, FactKind.SHAPE, FactKind.MEMBERS);
    assertThat(cast(HOLD + "    return (Integer) v;\n", INTEGER_TARGET)).containsExactly("4:12");
    assertThat(cast(HOLD + "    return (CharSequence) v;\n", CHARS_TARGET)).isEmpty();
  }

  @Test
  void aReassignedVariableOrAParameterIsNotKnown() {
    assertThat(cast(HOLD + "    v = p;\n    return (Integer) v;\n", INTEGER_TARGET)).isEmpty();
    assertThat(cast("    return (Integer) p;\n", INTEGER_TARGET)).isEmpty();
    assertThat(cast("    Object v;\n    return (Integer) v;\n", INTEGER_TARGET)).isEmpty();
    assertThat(cast(HOLD + "    v++;\n    return (Integer) v;\n", INTEGER_TARGET)).isEmpty();
  }

  @Test
  void pathsThatAgreeKeepTheTypeAndPathsThatDisagreeDropIt() {
    final String branches =
        "    Object v;\n    if (c) {\n      v = \"text\";\n    } else {\n      v = %s;\n    }\n"
            + "    return (Integer) v;\n";

    assertThat(cast(branches.formatted(TEXT), INTEGER_TARGET)).hasSize(1);
    assertThat(cast(branches.formatted("p"), INTEGER_TARGET)).isEmpty();
    assertThat(
            cast(
                HOLD + "    while (c) {\n      v = p;\n    }\n    return (Integer) v;\n",
                INTEGER_TARGET))
        .isEmpty();
    assertThat(
            cast(
                HOLD + "    while (c) {\n      c = !c;\n    }\n    return (Integer) v;\n",
                INTEGER_TARGET))
        .hasSize(1);
  }

  @Test
  void anInstanceofTestThatCanNeverBeTrueIsReported() {
    assertThat(
            run(
                ImpossibleConversionCheck.Mode.INSTANCEOF,
                HOLD + "    return v instanceof Integer;\n",
                INTEGER_TARGET))
        .containsExactly("4:12");
    assertThat(
            run(
                ImpossibleConversionCheck.Mode.INSTANCEOF,
                HOLD + "    return v instanceof CharSequence;\n",
                CHARS_TARGET))
        .isEmpty();
  }

  @Test
  void aValueOfExactlyOneTypeCastToItsSubtypeIsADowncast() {
    final String statements = "    Object v = new Base();\n    return (Child) v;\n";

    assertThat(run(ImpossibleConversionCheck.Mode.DOWNCAST, statements, CHILD_TARGET))
        .containsExactly("4:12");
    assertThat(run(ImpossibleConversionCheck.Mode.CAST, statements, CHILD_TARGET)).isEmpty();
    assertThat(
            run(
                ImpossibleConversionCheck.Mode.DOWNCAST,
                "    Object v = new Base();\n    return (Integer) v;\n",
                INTEGER_TARGET))
        .isEmpty();
    assertThat(cast("    Object v = new Base();\n    return (Integer) v;\n", INTEGER_TARGET))
        .hasSize(1);
  }

  @Test
  void aValueThatMightBeASubclassOrWhoseSupertypesAreUnknownIsNotReported() {
    assertThat(
            run(
                ImpossibleConversionCheck.Mode.DOWNCAST,
                "    Object v = make();\n    return (Child) v;\n",
                CHILD_TARGET))
        .isEmpty();
    assertThat(
            new ParsedFacts(
                    "final class C {\n  Object t() {\n"
                        + HOLD
                        + "    return (Integer) v;\n  }\n}\n")
                .at(EXPRESSION, TEXT, ParsedFacts.typed(STRING, TypeShape.Kind.CLASS, true))
                .at(TYPE_TYPE, "Integer", INTEGER_TARGET)
                .run(new ImpossibleConversionCheck(ImpossibleConversionCheck.Mode.CAST, "message")))
        .isEmpty();
    assertThat(
            new ParsedFacts(
                    "final class C {\n  Object t() {\n"
                        + HOLD
                        + "    return (Integer) v;\n  }\n}\n")
                .run(new ImpossibleConversionCheck(ImpossibleConversionCheck.Mode.CAST, "message")))
        .isEmpty();
  }

  @Test
  void aPrimitiveArrayCastToAnotherPrimitiveArrayTypeIsReported() {
    final SpanFacts ints = ParsedFacts.type(new TypeRef("int", List.of(), 1, true));
    final SpanFacts longs = ParsedFacts.type(new TypeRef("long", List.of(), 1, true));
    final String statements = "    Object v = new int[3];\n    return (%s) v;\n";

    assertThat(arrays(statements.formatted("long[]"), ints, longs)).containsExactly("4:12");
    assertThat(arrays(statements.formatted("int[]"), ints, ints)).isEmpty();
    assertThat(arrays(statements.formatted("long[][]"), ints, longs)).hasSize(1);
  }

  private static List<String> arrays(
      final String statements, final SpanFacts value, final SpanFacts target) {
    return new ParsedFacts("final class C {\n  Object t() {\n" + statements + "  }\n}\n")
        .at(EXPRESSION, "newint[3]", value)
        .at(TYPE_TYPE, "long[]", target)
        .at(TYPE_TYPE, "int[]", target)
        .at(TYPE_TYPE, "long[][]", ParsedFacts.type(new TypeRef("long", List.of(), 2, true)))
        .run(
            new ImpossibleConversionCheck(
                ImpossibleConversionCheck.Mode.PRIMITIVE_ARRAY_CAST, "message"));
  }
}
