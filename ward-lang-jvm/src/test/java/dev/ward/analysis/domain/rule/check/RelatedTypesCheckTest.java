package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.List;
import org.junit.jupiter.api.Test;

class RelatedTypesCheckTest {

  private static final String EXPRESSION = "expression";

  private static final String EQUALS_SOURCE =
      "class C {\n  boolean t(Token token, Task task) {\n    return token.equals(task);\n  }\n}\n";
  private static final String POINTER_SOURCE =
      "class C {\n  boolean t(A left, B right) {\n    return left == right;\n  }\n}\n";
  private static final TypeRef TOKEN = TypeRef.of("app.Token");
  private static final TypeRef TASK = TypeRef.of("app.Task");
  private static final TypeRef LEFT = TypeRef.of("app.A");
  private static final TypeRef RIGHT = TypeRef.of("app.B");

  private static List<String> equalsRun(
      final RelatedTypesCheck.Pair pair, final boolean isFinal, final TypeRef... tokenAbove) {
    return new ParsedFacts(EQUALS_SOURCE)
        .at(
            EXPRESSION,
            "token",
            ParsedFacts.typed(TOKEN, TypeShape.Kind.CLASS, isFinal, tokenAbove))
        .at(EXPRESSION, "task", ParsedFacts.typed(TASK, TypeShape.Kind.INTERFACE, false))
        .run(new RelatedTypesCheck(RelatedTypesCheck.Comparison.EQUALS, pair, "message"));
  }

  private static List<String> pointerRun(final TypeRef... leftAbove) {
    return new ParsedFacts(POINTER_SOURCE)
        .at(EXPRESSION, "left", ParsedFacts.typed(LEFT, TypeShape.Kind.INTERFACE, false, leftAbove))
        .at(EXPRESSION, "right", ParsedFacts.typed(RIGHT, TypeShape.Kind.INTERFACE, false))
        .run(
            new RelatedTypesCheck(
                RelatedTypesCheck.Comparison.POINTER,
                RelatedTypesCheck.Pair.INTERFACES,
                "message"));
  }

  @Test
  void aFinalClassThatDoesNotImplementTheInterfaceIsReported() {
    assertThat(equalsRun(RelatedTypesCheck.Pair.CLASS_AND_INTERFACE, true, ParsedFacts.OBJECT))
        .containsExactly("3:12");
    assertThat(equalsRun(RelatedTypesCheck.Pair.CLASS_AND_INTERFACE, true, TASK)).isEmpty();
    assertThat(equalsRun(RelatedTypesCheck.Pair.CLASS_AND_INTERFACE, false, ParsedFacts.OBJECT))
        .isEmpty();
  }

  private static List<String> finalKind(final TypeShape.Kind kind) {
    return new ParsedFacts(EQUALS_SOURCE)
        .at(EXPRESSION, "token", ParsedFacts.typed(TOKEN, kind, false))
        .at(EXPRESSION, "task", ParsedFacts.typed(TASK, TypeShape.Kind.INTERFACE, false))
        .run(
            new RelatedTypesCheck(
                RelatedTypesCheck.Comparison.EQUALS,
                RelatedTypesCheck.Pair.CLASS_AND_INTERFACE,
                "message"));
  }

  @Test
  void enumsAndRecordsCountAsFinalClasses() {
    assertThat(finalKind(TypeShape.Kind.ENUM)).hasSize(1);
    assertThat(finalKind(TypeShape.Kind.RECORD)).hasSize(1);
  }

  @Test
  void twoUnrelatedInterfacesAreReportedForEqualsAndPointerComparison() {
    assertThat(pointerRun()).containsExactly("3:12");
    assertThat(pointerRun(RIGHT)).isEmpty();
    assertThat(
            new ParsedFacts(EQUALS_SOURCE)
                .at(EXPRESSION, "token", ParsedFacts.typed(TOKEN, TypeShape.Kind.INTERFACE, false))
                .at(EXPRESSION, "task", ParsedFacts.typed(TASK, TypeShape.Kind.INTERFACE, false))
                .run(
                    new RelatedTypesCheck(
                        RelatedTypesCheck.Comparison.EQUALS,
                        RelatedTypesCheck.Pair.INTERFACES,
                        "message")))
        .hasSize(1);
  }

  @Test
  void unknownFactsSameTypesPrimitivesAndOtherCallsAreNotReported() {
    final RelatedTypesCheck check =
        new RelatedTypesCheck(
            RelatedTypesCheck.Comparison.EQUALS,
            RelatedTypesCheck.Pair.CLASS_AND_INTERFACE,
            "message");

    assertThat(new ParsedFacts(EQUALS_SOURCE).run(check)).isEmpty();
    assertThat(new ParsedFacts(EQUALS_SOURCE).plain(check)).isEmpty();
    assertThat(
            new ParsedFacts(EQUALS_SOURCE)
                .at(EXPRESSION, "token", ParsedFacts.typed(TASK, TypeShape.Kind.CLASS, true))
                .at(EXPRESSION, "task", ParsedFacts.typed(TASK, TypeShape.Kind.INTERFACE, false))
                .run(check))
        .isEmpty();
    assertThat(
            new ParsedFacts(EQUALS_SOURCE)
                .at(EXPRESSION, "token", ParsedFacts.type(TOKEN))
                .at(EXPRESSION, "task", ParsedFacts.type(TASK))
                .run(check))
        .isEmpty();
    assertThat(
            new ParsedFacts(
                    "class C {\n  boolean t(Token a, Task b) {\n    return a.same(b);\n  }\n}\n")
                .at(EXPRESSION, "a", ParsedFacts.typed(TOKEN, TypeShape.Kind.CLASS, true))
                .at(EXPRESSION, "b", ParsedFacts.typed(TASK, TypeShape.Kind.INTERFACE, false))
                .run(check))
        .isEmpty();
    assertThat(
            new ParsedFacts(EQUALS_SOURCE.replace("equals(task)", "equals(task, task)")).run(check))
        .isEmpty();
    assertThat(
            new ParsedFacts(POINTER_SOURCE.replace("==", "+"))
                .at(EXPRESSION, "left", ParsedFacts.typed(LEFT, TypeShape.Kind.INTERFACE, false))
                .at(EXPRESSION, "right", ParsedFacts.typed(RIGHT, TypeShape.Kind.INTERFACE, false))
                .run(
                    new RelatedTypesCheck(
                        RelatedTypesCheck.Comparison.POINTER,
                        RelatedTypesCheck.Pair.INTERFACES,
                        "message")))
        .isEmpty();
  }
}
