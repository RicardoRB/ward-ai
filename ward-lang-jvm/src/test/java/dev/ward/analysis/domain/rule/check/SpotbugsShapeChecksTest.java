package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The class-shape and cast checks that port SpotBugs patterns, on parsed Java sources. */
class SpotbugsShapeChecksTest {

  private static final String MESSAGE = "message";

  @Test
  void covariantCompareToIsReportedOnlyForAClassWithNothingInherited() {
    final CovariantCompareToCheck concrete = new CovariantCompareToCheck(false, MESSAGE);
    final String covariant = " Point {\n  int compareTo(Point p) {\n    return 0;\n  }\n}\n";

    assertThat(new ParsedFacts("class" + covariant).run(concrete)).containsExactly("1:1");
    assertThat(new ParsedFacts("abstract class" + covariant).run(concrete)).isEmpty();
    assertThat(
            new ParsedFacts("class Point extends Base {\n  int compareTo(Point p) {}\n}\n")
                .run(concrete))
        .isEmpty();
    assertThat(
            new ParsedFacts(
                    "class Point implements Comparable<Point> {\n  int compareTo(Point p) {}\n}\n")
                .run(concrete))
        .isEmpty();
    assertThat(
            new ParsedFacts(
                    "class Point {\n  int compareTo(Point p) {}\n  int compareTo(Object o) {}\n}\n")
                .run(concrete))
        .isEmpty();
  }

  @Test
  void abstractCovariantCompareToIsReportedForAbstractClassesOnly() {
    final CovariantCompareToCheck abstractOnes = new CovariantCompareToCheck(true, MESSAGE);

    assertThat(
            new ParsedFacts("abstract class Shape {\n  int compareTo(Shape s) {}\n}\n")
                .run(abstractOnes))
        .hasSize(1);
    assertThat(new ParsedFacts("class Shape {\n  int compareTo(Shape s) {}\n}\n").run(abstractOnes))
        .isEmpty();
  }

  @Test
  void aThrowInAConstructorOfAnOpenClassIsReported() {
    final ConstructorThrowCheck check = new ConstructorThrowCheck(MESSAGE);
    final String body =
        "(int n) {\n    if (n < 0) {\n      throw new RuntimeException();\n    }\n  }\n";

    assertThat(new ParsedFacts("class A {\n  A" + body + "}\n").run(check)).containsExactly("2:3");
    assertThat(new ParsedFacts("final class A {\n  A" + body + "}\n").run(check)).isEmpty();
    assertThat(new ParsedFacts("class A {\n  private A" + body + "}\n").run(check)).isEmpty();
    assertThat(new ParsedFacts("class A {\n  A() {}\n}\n").run(check)).isEmpty();
    assertThat(
            new ParsedFacts("class A {\n  A" + body + "  protected final void finalize() {}\n}\n")
                .run(check))
        .isEmpty();
  }

  @Test
  void anAssertOnAParameterOfAnExposedMethodIsReported() {
    final AssertOnParameterCheck check = new AssertOnParameterCheck(MESSAGE);

    assertThat(
            new ParsedFacts("class A {\n  public void f(int n) {\n    assert n > 0;\n  }\n}\n")
                .run(check))
        .containsExactly("3:5");
    assertThat(
            new ParsedFacts(
                    "class A {\n  protected A(int n) {\n    assert n > 0 : \"n\";\n  }\n}\n")
                .run(check))
        .hasSize(1);
    assertThat(
            new ParsedFacts("class A {\n  void f(int n) {\n    assert n > 0;\n  }\n}\n").run(check))
        .isEmpty();
    assertThat(
            new ParsedFacts("class A {\n  public void f(int n) {\n    assert true;\n  }\n}\n")
                .run(check))
        .isEmpty();
    assertThat(
            new ParsedFacts(
                    "class A {\n  public static int x;\n  static {\n    assert x > 0;\n  }\n}\n")
                .run(check))
        .isEmpty();
  }

  @Test
  void aCastBetweenTheNamedTypesIsReportedOnlyWhenTheValueTypeIsKnown() {
    final CastTypesCheck check =
        new CastTypesCheck(Set.of("java.util.Collection"), Set.of("List"), MESSAGE);
    final String source =
        "class A {\n  Object f(Object c) {\n    return (List<String>) c;\n  }\n}\n";
    final SpanFacts collection = ParsedFacts.type(TypeRef.of("java.util.Collection"));

    assertThat(new ParsedFacts(source).at("expression", "c", collection).run(check))
        .containsExactly("3:12");
    assertThat(new ParsedFacts(source).run(check)).isEmpty();
    assertThat(
            new ParsedFacts(source)
                .at("expression", "c", ParsedFacts.type(TypeRef.of("java.util.Set")))
                .run(check))
        .isEmpty();
    assertThat(
            new ParsedFacts(source.replace("List<String>", "Object"))
                .at("expression", "c", collection)
                .run(check))
        .isEmpty();
  }
}
