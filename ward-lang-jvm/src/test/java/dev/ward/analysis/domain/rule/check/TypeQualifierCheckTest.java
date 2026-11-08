package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TypeQualifierCheckTest {

  private static final String HEADER =
      "@TypeQualifier @interface Tainted { When when() default When.ALWAYS; }\n"
          + "@TypeQualifier @interface Strict {}\n"
          + "class C {\n";

  private static SpanFacts call(final String name, final int parameters) {
    return new SpanFacts(
        null,
        null,
        new CallTarget(
            TypeRef.of("C"),
            name,
            Collections.nCopies(parameters, TypeRef.of("java.lang.String")),
            null,
            false,
            false,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  private static List<String> run(
      final TypeQualifierCheck.Mode mode, final String body, final String... calls) {
    ParsedFacts parsed = new ParsedFacts(HEADER + body + "}\n");
    for (final String text : calls) {
      final String name = text.substring(0, text.indexOf('('));
      final int count = text.endsWith("()") ? 0 : text.split(",").length;
      parsed = parsed.at("expression", text, call(name, count));
    }
    return parsed.run(new TypeQualifierCheck(mode, "message"));
  }

  @Test
  void aValueThatAlwaysHasTheQualifierReachingAPlaceThatRequiresItNeverIsReported() {
    final String body =
        "  void sink(@Tainted(when = When.NEVER) String s) {}\n  void t(@Tainted String x) {\n    sink(x);\n  }\n";
    assertThat(run(TypeQualifierCheck.Mode.ALWAYS_VALUE_USED_WHERE_NEVER_REQUIRED, body, "sink(x)"))
        .containsExactly("6:10");
    assertThat(run(TypeQualifierCheck.Mode.NEVER_VALUE_USED_WHERE_ALWAYS_REQUIRED, body, "sink(x)"))
        .isEmpty();
    assertThat(run(TypeQualifierCheck.Mode.ALWAYS_VALUE_USED_WHERE_NEVER_REQUIRED, body)).isEmpty();
    assertThat(
            new TypeQualifierCheck(
                    TypeQualifierCheck.Mode.ALWAYS_VALUE_USED_WHERE_NEVER_REQUIRED, "m")
                .needs()
                .facts())
        .containsExactly(FactKind.CALL);
  }

  @Test
  void aValueThatNeverHasTheQualifierReachingAPlaceThatRequiresItAlwaysIsReported() {
    final String body =
        "  void need(@Tainted String s) {}\n  void t(@Tainted(when = When.NEVER) String x) {\n    String y = x;\n    need(y);\n  }\n";
    assertThat(run(TypeQualifierCheck.Mode.NEVER_VALUE_USED_WHERE_ALWAYS_REQUIRED, body, "need(y)"))
        .containsExactly("7:10");
    final String same = body.replace("when = When.NEVER", "when = When.ALWAYS");
    assertThat(run(TypeQualifierCheck.Mode.NEVER_VALUE_USED_WHERE_ALWAYS_REQUIRED, same, "need(y)"))
        .isEmpty();
  }

  @Test
  void anUnknownValueReachingAPlaceThatStrictlyRequiresTheQualifierIsReported() {
    final String body =
        "  void need(@Strict String s) {}\n  void t(String x, @Strict String ok) {\n    need(x);\n    need(ok);\n  }\n";
    assertThat(
            run(
                TypeQualifierCheck.Mode.UNKNOWN_VALUE_USED_WHERE_ALWAYS_STRICTLY_REQUIRED,
                body,
                "need(x)",
                "need(ok)"))
        .containsExactly("6:10");
  }

  @Test
  void aValueThatMayHaveTheQualifierOrIsExplicitlyUnknownReachingARequirementIsReported() {
    final String need =
        "  void need(@Tainted String s) {}\n"
            + "  void never(@Tainted(when = When.NEVER) String s) {}\n";
    final String maybe =
        need + "  void t(@Tainted(when = When.MAYBE) String x) {\n    need(x);\n  }\n";
    assertThat(
            run(TypeQualifierCheck.Mode.MAYBE_SOURCE_VALUE_REACHES_ALWAYS_SINK, maybe, "need(x)"))
        .containsExactly("7:10");
    final String maybeNever = maybe.replace("need(x)", "never(x)");
    assertThat(
            run(
                TypeQualifierCheck.Mode.MAYBE_SOURCE_VALUE_REACHES_NEVER_SINK,
                maybeNever,
                "never(x)"))
        .containsExactly("7:11");
    final String unknown =
        need + "  void t(@Tainted(when = When.UNKNOWN) String x) {\n    need(x);\n  }\n";
    assertThat(
            run(
                TypeQualifierCheck.Mode.EXPLICIT_UNKNOWN_SOURCE_VALUE_REACHES_ALWAYS_SINK,
                unknown,
                "need(x)"))
        .containsExactly("7:10");
    assertThat(
            run(
                TypeQualifierCheck.Mode.EXPLICIT_UNKNOWN_SOURCE_VALUE_REACHES_NEVER_SINK,
                unknown.replace("need(x)", "never(x)"),
                "never(x)"))
        .containsExactly("7:11");
    assertThat(
            run(
                TypeQualifierCheck.Mode.EXPLICIT_UNKNOWN_SOURCE_VALUE_REACHES_NEVER_SINK,
                unknown,
                "need(x)"))
        .isEmpty();
  }

  @Test
  void twoComparedValuesThatCannotBothHaveTheQualifierAreReported() {
    final String body =
        "  boolean t(@Tainted String a, @Tainted(when = When.NEVER) String b) {\n    return a == b;\n  }\n";
    assertThat(
            run(TypeQualifierCheck.Mode.COMPARING_VALUES_WITH_INCOMPATIBLE_TYPE_QUALIFIERS, body))
        .containsExactly("5:12");
    assertThat(
            run(
                TypeQualifierCheck.Mode.COMPARING_VALUES_WITH_INCOMPATIBLE_TYPE_QUALIFIERS,
                body.replace("a == b", "a.equals(b)"),
                "a.equals(b)"))
        .containsExactly("5:12");
    assertThat(
            run(
                TypeQualifierCheck.Mode.COMPARING_VALUES_WITH_INCOMPATIBLE_TYPE_QUALIFIERS,
                body.replace("a == b", "a == \"x\"")))
        .isEmpty();
  }

  @Test
  void aCallOrFieldOfTheFileThatIsUsedDirectlyAsAValueIsASource() {
    final String body =
        "  @Tainted(when = When.NEVER) String make() { return null; }\n"
            + "  void need(@Tainted String s) {}\n  void t() {\n    need(make());\n  }\n";
    assertThat(
            run(
                TypeQualifierCheck.Mode.NEVER_VALUE_USED_WHERE_ALWAYS_REQUIRED,
                body,
                "need(make())",
                "make()"))
        .containsExactly("7:10");
    final String field =
        "  @Tainted(when = When.NEVER) String f;\n  void need(@Tainted String s) {}\n  void t() {\n    need(this.f);\n  }\n";
    assertThat(
            run(
                TypeQualifierCheck.Mode.NEVER_VALUE_USED_WHERE_ALWAYS_REQUIRED,
                field,
                "need(this.f)"))
        .containsExactly("7:10");
    assertThat(
            run(
                TypeQualifierCheck.Mode.NEVER_VALUE_USED_WHERE_ALWAYS_REQUIRED,
                body.replace("need(make())", "need(other())"),
                "need(other())",
                "other()"))
        .isEmpty();
  }

  @Test
  void aReturnOrStoreRequiringAQualifierIsASink() {
    final String body =
        "  @Tainted String f;\n  @Tainted String t(@Tainted(when = When.NEVER) String x) {\n    this.f = x;\n    return x;\n  }\n";
    assertThat(run(TypeQualifierCheck.Mode.NEVER_VALUE_USED_WHERE_ALWAYS_REQUIRED, body))
        .hasSize(2);
  }

  @Test
  void unknownQualifiersAndConsistentFlowsAreNotReported() {
    final String body =
        "  void need(@Other String s) {}\n  void t(@Other(when = When.NEVER) String x) {\n    need(x);\n  }\n";
    assertThat(run(TypeQualifierCheck.Mode.NEVER_VALUE_USED_WHERE_ALWAYS_REQUIRED, body, "need(x)"))
        .isEmpty();
    final String fine =
        "  void need(@Tainted String s) {}\n  void t(@Tainted String x) {\n    need(x);\n  }\n";
    for (final TypeQualifierCheck.Mode mode : TypeQualifierCheck.Mode.values()) {
      assertThat(run(mode, fine, "need(x)")).isEmpty();
    }
    assertThat(
            new ParsedFacts("class C {}")
                .plain(new TypeQualifierCheck(TypeQualifierCheck.Mode.values()[0], "m")))
        .isEmpty();
  }
}
