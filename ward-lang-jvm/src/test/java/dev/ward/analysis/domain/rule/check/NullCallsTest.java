package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.NullTestSupport.run;
import static dev.ward.analysis.domain.rule.check.NullTestSupport.target;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.types.SpanFacts;
import org.junit.jupiter.api.Test;

class NullCallsTest {

  @Test
  void aNullPassedForAParameterThatMustNotBeNullIsReported() {
    final String body = "  void t(Api api) {\n    api.take(null);\n  }\n";
    final SpanFacts annotated = target("Api", "take", 1, "javax.annotation.Nonnull");
    assertThat(
            run(NullDataflowCheck.Mode.NONNULL_PARAM_VIOLATION, body, "api.take(null)", annotated))
        .containsExactly("3:5");
    assertThat(
            run(
                NullDataflowCheck.Mode.NONNULL_PARAM_VIOLATION,
                body,
                "api.take(null)",
                target("Api", "take", 1)))
        .isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.NONNULL_PARAM_VIOLATION,
                body.replace("null", "\"x\""),
                "api.take(\"x\")",
                annotated))
        .isEmpty();
    assertThat(run(NullDataflowCheck.Mode.NONNULL_PARAM_VIOLATION, body)).isEmpty();
  }

  @Test
  void aNullPassedToAParameterTheCalleeDereferencesIsReportedByHowTheCalleeIsBound() {
    final String body =
        "  private void use(String s) {\n    s.length();\n  }\n  void t() {\n    use(null);\n  }\n";
    final SpanFacts call = target("C", "use", 1);
    assertThat(run(NullDataflowCheck.Mode.NULL_PARAM_DEREF_NONVIRTUAL, body, "use(null)", call))
        .containsExactly("6:5");
    final String open = body.replace("private ", "");
    assertThat(run(NullDataflowCheck.Mode.NULL_PARAM_DEREF_NONVIRTUAL, open, "use(null)", call))
        .isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS,
                open,
                "use(null)",
                call))
        .isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS,
                open.replace("void use", "final void use"),
                "use(null)",
                call))
        .containsExactly("6:5");
    assertThat(
            run(
                NullDataflowCheck.Mode.NULL_PARAM_DEREF_NONVIRTUAL,
                body.replace("s.length();", "if (s != null) {\n      s.length();\n    }"),
                "use(null)",
                call))
        .isEmpty();
    assertThat(run(NullDataflowCheck.Mode.NULL_PARAM_DEREF_NONVIRTUAL, body)).isEmpty();
  }

  @Test
  void aNullReturnedOrStoredWhereItMustNotBeIsReported() {
    final String returned = "  @NonNull String t() {\n    String s = null;\n    return s;\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.NONNULL_RETURN_VIOLATION, returned))
        .containsExactly("4:12");
    assertThat(
            run(NullDataflowCheck.Mode.NONNULL_RETURN_VIOLATION, returned.replace("@NonNull ", "")))
        .isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.NONNULL_RETURN_VIOLATION,
                returned.replace("String t()", "String toString()")))
        .isEmpty();
    final String stored = "  @NonNull String f;\n  void t() {\n    this.f = null;\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.STORE_INTO_NONNULL_FIELD, stored)).containsExactly("4:5");
    assertThat(
            run(NullDataflowCheck.Mode.STORE_INTO_NONNULL_FIELD, stored.replace("@NonNull ", "")))
        .isEmpty();
  }
}
