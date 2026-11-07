package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FlowNullnessFlowTest {

  private static final String DEREF = "np-always-null";
  private static final String SOME = "np-null-on-some-path";

  private static List<String> run(final String rule, final String parameters, final String body) {
    return FlowNullSources.run(rule, FlowNullSources.method(parameters, body));
  }

  @Test
  void aLoopReachesAFixedPoint() {
    assertThat(
            run(
                SOME,
                "boolean b",
                "String s = null;\nwhile (b) {\n  s.length();\n  s = null;\n  if (b) { s = \"x\"; }\n}"))
        .containsExactly("5:3");
  }

  @Test
  void aTernaryJoinsItsBranchesAndACastKeepsTheValue() {
    assertThat(run(SOME, "boolean b", "String s = b ? null : \"x\";\ns.length();"))
        .containsExactly("4:1");
    assertThat(run(DEREF, "", "Object o = (Object) null;\no.hashCode();")).containsExactly("4:1");
  }

  @Test
  void aVariableACapturedLambdaMayChangeIsUnknown() {
    assertThat(run(DEREF, "", "String s = null;\nRunnable r = () -> s.length();\ns.length();"))
        .isEmpty();
  }

  @Test
  void anUnknownValueNeverReports() {
    assertThat(
            run(DEREF, "String p", "String s = p;\ns.length();\nString t = make();\nt.length();"))
        .isEmpty();
    assertThat(run(SOME, "String p", "p.length();")).isEmpty();
  }

  @Test
  void anArrayAccessAndAThrowDereference() {
    assertThat(run(DEREF, "", "int[] a = null;\nint x = a[0];")).containsExactly("4:9");
    assertThat(run(DEREF, "", "RuntimeException e = null;\nthrow e;")).containsExactly("4:7");
  }

  @Test
  void aCallOfAMethodOfTheFileChecksItsArgumentsAgainstItsAnnotations() {
    final String source =
        "class A {\n  void take(@NonNull String v) {}\n  void run(@Nullable String m) {\n    take(m);\n    this.take(m);\n  }\n}\n";
    assertThat(FlowNullSources.run("tq-maybe-source-value-reaches-always-sink", source))
        .containsExactly("4:10", "5:15");
  }

  @Test
  void aFieldStoreIsCheckedAgainstTheFieldAnnotation() {
    final String source =
        "class A {\n  @NonNull String f;\n  void run() {\n    this.f = null;\n    f = null;\n  }\n}\n";
    assertThat(FlowNullSources.run("np-store-into-nonnull-field", source))
        .containsExactly("4:14", "5:9");
  }
}
