package dev.ward.analysis.domain.jvm.types;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeMembers;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnnotationValuesFactsTest {

  private static final String DEPRECATED = "java.lang.Deprecated";
  private static final String HELPER =
      "package app;\n"
          + "@Deprecated(since = \"1\", forRemoval = true)\n"
          + "public class Helper {\n"
          + "  @SuppressWarnings({\"a\", \"b\"}) @Deprecated(since = \"9\") public void old() {}\n"
          + "  @Pinned(Helper.class) @Level(Mode.STRICT) @Size(3) @Flag(true) public void tagged() {}\n"
          + "}\n";

  private static String body(final String statements) {
    return "package app;\npublic class App {\n  void go(Helper h) {\n" + statements + "\n  }\n}\n";
  }

  private static Probe probe(final String statements) {
    return new Probe(body(statements), Map.of("app.Helper", HELPER));
  }

  @Test
  void aSourceMethodCarriesTheElementValuesOfItsAnnotations() {
    final CallTarget target = probe("    h.old();").call("h.old()").orElseThrow();

    assertThat(target.annotationValues())
        .containsEntry("java.lang.SuppressWarnings#value", List.of("a", "b"))
        .containsEntry(DEPRECATED + "#since", List.of("9"));
    assertThat(target.declaringAnnotationValues())
        .containsEntry(DEPRECATED + "#since", List.of("1"))
        .containsEntry(DEPRECATED + "#forRemoval", List.of("true"));
  }

  @Test
  void classEnumNumberAndFlagValuesAreKeptAsNamesAndText() {
    final CallTarget target = probe("    h.tagged();").call("h.tagged()").orElseThrow();

    assertThat(target.annotationValues().values()).isNotEmpty();
    assertThat(target.annotationValues().entrySet())
        .extracting(Map.Entry::getKey)
        .anyMatch(key -> key.endsWith("Pinned#value"))
        .anyMatch(key -> key.endsWith("Level#value"));
    assertThat(
            target.annotationValues().entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("#value"))
                .flatMap(entry -> entry.getValue().stream())
                .toList())
        .contains("app.Helper", "STRICT", "3", "true");
  }

  @Test
  void aJdkMethodCarriesTheElementValuesOfItsClassFile() {
    final CallTarget target =
        Probe.of("package app;\npublic class App {\n  void go(Thread t) {\n    t.stop();\n  }\n}\n")
            .call("t.stop()")
            .orElseThrow();

    assertThat(target.annotationValues())
        .containsEntry(DEPRECATED + "#forRemoval", List.of("true"));
    assertThat(target.annotationValues().get(DEPRECATED + "#since")).isNotEmpty();
  }

  @Test
  void theMembersOfAClassCarryTheValuesOfItsAnnotations() {
    final Probe probe = probe("    var n = h;");

    final TypeMembers members = probe.at(probe.span("h")).membersOf(probe.span("h")).orElseThrow();

    assertThat(members.annotationValues())
        .containsEntry(DEPRECATED + "#forRemoval", List.of("true"));
  }
}
