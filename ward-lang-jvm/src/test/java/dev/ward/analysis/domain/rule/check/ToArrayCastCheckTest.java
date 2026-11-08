package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToArrayCastCheckTest {

  private static final ToArrayCastCheck CHECK = new ToArrayCastCheck("message");
  private static final String SOURCE =
      "final class C {\n  Object t(java.util.List<String> l) {\n    return (String[]) l.toArray();\n  }\n}\n";
  private static final TypeRef OBJECTS = new TypeRef("java.lang.Object", List.of(), 1, false);
  private static final TypeRef STRINGS = new TypeRef("java.lang.String", List.of(), 1, false);

  private static SpanFacts call(final String owner, final List<TypeRef> parameters) {
    return new SpanFacts(
        null,
        null,
        new CallTarget(
            TypeRef.of(owner),
            "toArray",
            parameters,
            OBJECTS,
            false,
            false,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            Map.of(),
            Map.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  private static List<String> run(final String source, final SpanFacts call, final TypeRef target) {
    return new ParsedFacts(source)
        .at("expression", "l.toArray()", call)
        .at("typeType", "String[]", ParsedFacts.type(target))
        .run(CHECK);
  }

  @Test
  void aCastOfThePlainToArrayToAnArrayTypeIsReported() {
    assertThat(CHECK.needs().facts()).containsExactlyInAnyOrder(FactKind.CALL, FactKind.TYPE);
    assertThat(run(SOURCE, call("java.util.Collection", List.of()), STRINGS))
        .containsExactly("3:12");
  }

  @Test
  void anObjectArrayTargetAnotherMethodOrUnknownFactsAreNotReported() {
    assertThat(run(SOURCE, call("java.util.Collection", List.of()), OBJECTS)).isEmpty();
    assertThat(run(SOURCE, call("app.Bag", List.of()), STRINGS)).isEmpty();
    assertThat(run(SOURCE, call("java.util.Collection", List.of(OBJECTS)), STRINGS)).isEmpty();
    assertThat(run(SOURCE, ParsedFacts.type(STRINGS), STRINGS)).isEmpty();
    assertThat(
            run(
                SOURCE.replace("(String[]) ", ""),
                call("java.util.Collection", List.of()),
                STRINGS))
        .isEmpty();
    assertThat(new ParsedFacts(SOURCE).run(CHECK)).isEmpty();
    assertThat(new ParsedFacts(SOURCE).plain(CHECK)).isEmpty();
  }
}
