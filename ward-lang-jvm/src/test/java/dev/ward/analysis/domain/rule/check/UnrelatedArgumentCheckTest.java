package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnrelatedArgumentCheckTest {

  private static final String LIST_NAME = "java.util.List";

  private static final UnrelatedArgumentCheck CHECK = new UnrelatedArgumentCheck("message");
  private static final String LIST_SOURCE =
      "class C {\n  boolean t(Object names, Object id) {\n    return names.contains(id);\n  }\n}\n";
  private static final String MAP_SOURCE =
      "class C {\n  Object t(Object names, Object id) {\n    return names.get(id);\n  }\n}\n";
  private static final TypeRef STRING = TypeRef.of("java.lang.String");
  private static final TypeRef INTEGER = TypeRef.of("java.lang.Integer");
  private static final TypeRef NUMBER = TypeRef.of("java.lang.Number");
  private static final TypeRef LIST = generic(LIST_NAME, STRING);

  private static TypeRef generic(final String name, final TypeRef... arguments) {
    return new TypeRef(name, List.of(arguments), 0, false);
  }

  private static List<String> run(
      final String source, final TypeRef receiver, final SpanFacts argument) {
    return new ParsedFacts(source)
        .at("expression", "names", ParsedFacts.type(receiver))
        .at("expression", "id", argument)
        .run(CHECK);
  }

  private static SpanFacts finalType(final TypeRef type, final TypeRef... above) {
    return ParsedFacts.typed(type, TypeShape.Kind.CLASS, true, above);
  }

  @Test
  void aFinalArgumentTypeUnrelatedToTheElementTypeIsReported() {
    assertThat(run(LIST_SOURCE, LIST, finalType(INTEGER, NUMBER, ParsedFacts.OBJECT)))
        .containsExactly("3:12");
    assertThat(CHECK.needs().nodeKinds()).containsExactly("expression");
  }

  @Test
  void theElementTypeItselfOrOneOfItsSubtypesIsNotReported() {
    assertThat(run(LIST_SOURCE, LIST, finalType(STRING, ParsedFacts.OBJECT))).isEmpty();
    assertThat(run(LIST_SOURCE, LIST, finalType(INTEGER, STRING))).isEmpty();
  }

  @Test
  void aNonFinalArgumentUnknownFactsOrAnArrayAreNotReported() {
    assertThat(
            run(LIST_SOURCE, LIST, ParsedFacts.typed(INTEGER, TypeShape.Kind.CLASS, false, NUMBER)))
        .isEmpty();
    assertThat(
            run(
                LIST_SOURCE,
                LIST,
                ParsedFacts.typed(INTEGER, TypeShape.Kind.INTERFACE, true, NUMBER)))
        .isEmpty();
    assertThat(run(LIST_SOURCE, LIST, ParsedFacts.type(INTEGER))).isEmpty();
    assertThat(
            run(
                LIST_SOURCE,
                LIST,
                ParsedFacts.type(new TypeRef("java.lang.Integer", List.of(), 1, false))))
        .isEmpty();
    assertThat(new ParsedFacts(LIST_SOURCE).run(CHECK)).isEmpty();
    assertThat(new ParsedFacts(LIST_SOURCE).plain(CHECK)).isEmpty();
  }

  @Test
  void aPrimitiveArgumentIsComparedAsItsBox() {
    final SpanFacts primitive = ParsedFacts.type(new TypeRef("int", List.of(), 0, true));

    assertThat(run(LIST_SOURCE, generic(LIST_NAME, TypeRef.of("java.lang.Long")), primitive))
        .hasSize(1);
    assertThat(run(LIST_SOURCE, generic(LIST_NAME, INTEGER), primitive)).isEmpty();
    assertThat(run(LIST_SOURCE, generic(LIST_NAME, NUMBER), primitive)).isEmpty();
    assertThat(
            run(
                LIST_SOURCE,
                generic(LIST_NAME, STRING),
                ParsedFacts.type(new TypeRef("char", List.of(), 0, true))))
        .isEmpty();
  }

  @Test
  void aMapIsAskedByItsKeyAndOtherReceiversAndMethodsAreIgnored() {
    final SpanFacts integer = finalType(INTEGER, NUMBER, ParsedFacts.OBJECT);

    assertThat(run(MAP_SOURCE, generic("java.util.Map", STRING, INTEGER), integer)).hasSize(1);
    assertThat(run(MAP_SOURCE, generic("java.util.Map", INTEGER, STRING), integer)).isEmpty();
    assertThat(run(MAP_SOURCE, generic("app.Cache", STRING), integer)).isEmpty();
    assertThat(run(MAP_SOURCE, generic(LIST_NAME, STRING), integer)).isEmpty();
    assertThat(run(LIST_SOURCE.replace("contains", "add"), LIST, integer)).isEmpty();
    assertThat(run(LIST_SOURCE, generic(LIST_NAME, TypeRef.of("T")), integer)).isEmpty();
    assertThat(run(LIST_SOURCE, generic(LIST_NAME), integer)).isEmpty();
    assertThat(run(LIST_SOURCE.replace("contains(id)", "contains(id, id)"), LIST, integer))
        .isEmpty();
    assertThat(
            new ParsedFacts(
                    "class C {\n  int t(Object names) {\n    return names.size();\n  }\n}\n")
                .at("expression", "names", ParsedFacts.type(LIST))
                .run(CHECK))
        .isEmpty();
  }
}
