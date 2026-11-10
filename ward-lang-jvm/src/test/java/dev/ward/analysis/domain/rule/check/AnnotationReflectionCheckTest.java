package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnnotationReflectionCheckTest {

  private static final AnnotationReflectionCheck CHECK = new AnnotationReflectionCheck("message");
  private static final String USE =
      "final class C {\n  boolean t(Class<?> k) {\n    return k.isAnnotationPresent(Marker.class);\n  }\n}\n";

  private static java.util.List<String> run(final String source) {
    return new ParsedFacts(source).run(CHECK);
  }

  @Test
  void aQueryForAnAnnotationThatIsNotKeptAtRunTimeIsReported() {
    assertThat(run("@interface Marker {\n}\n" + USE)).containsExactly("5:14");
    assertThat(
            run(
                "@Retention(RetentionPolicy.CLASS)\n@interface Marker {\n}\n"
                    + USE.replace("isAnnotationPresent", "getAnnotation")))
        .hasSize(1);
    assertThat(run(USE.replace("Marker", "java.lang.Override"))).hasSize(1);
    assertThat(run(USE.replace("Marker", "SuppressWarnings"))).hasSize(1);
  }

  @Test
  void aRuntimeAnnotationAnUnknownOneOrAnotherMethodIsNotReported() {
    assertThat(run("@Retention(RetentionPolicy.RUNTIME)\n@interface Marker {\n}\n" + USE))
        .isEmpty();
    assertThat(run(USE)).isEmpty();
    assertThat(run("@interface Marker {\n}\n" + USE.replace("isAnnotationPresent", "isInstance")))
        .isEmpty();
    assertThat(run("@interface Marker {\n}\nfinal class C {\n  int t() {\n    return 1;\n  }\n}\n"))
        .isEmpty();
  }
}
