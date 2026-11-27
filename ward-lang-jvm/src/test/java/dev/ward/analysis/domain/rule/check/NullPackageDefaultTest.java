package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.SpanFacts;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A package's {@code @NullMarked}, read from the type facts, is the default of its classes. */
class NullPackageDefaultTest {
  private static final String MARKED = "org.jspecify.annotations.NullMarked";
  private static final String UNMARKED = "org.jspecify.annotations.NullUnmarked";
  private static final String PLAIN = "class A {\n  String f() {\n    return null;\n  }\n}\n";
  private static final String UNMARKED_CLASS =
      "@NullUnmarked\nclass A {\n  String f() {\n    return null;\n  }\n}\n";
  private static final String NESTED =
      "class A {\n  @NullUnmarked\n  static class B {\n    String f() {\n      return null;\n    }\n  }\n}\n";

  @Test
  void aMarkedPackageMakesTheClassMarked() {
    assertThat(run(PLAIN, List.of(MARKED)))
        .containsExactly("3:12");
  }

  @Test
  void aPackageWithoutNullMarkedLeavesTheClassUnmarked() {
    assertThat(run(PLAIN, List.of()))
        .isEmpty();
  }

  @Test
  void aNullUnmarkedPackageStaysUnmarked() {
    assertThat(run(PLAIN, List.of(MARKED, UNMARKED))).isEmpty();
  }

  @Test
  void aNullUnmarkedClassOverridesTheMarkedPackage() {
    assertThat(run(UNMARKED_CLASS, List.of(MARKED))).isEmpty();
  }

  @Test
  void aNullUnmarkedNestedClassOverridesTheMarkedPackage() {
    assertThat(run(NESTED, List.of(MARKED))).isEmpty();
  }

  private static List<String> run(final String source, final List<String> scopes) {
    final SpanFacts facts =
        new SpanFacts(null, null, null, List.of(), scopes, List.of(), List.of(), null, null);
    return new ParsedFacts(source)
        .atKind("classDeclaration", facts)
        .run(NullnessCheck.of("return-nullable", "{value}"));
  }
}
