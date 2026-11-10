package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.NullTestSupport.run;
import static dev.ward.analysis.domain.rule.check.NullTestSupport.target;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NullDoomedTest {

  @Test
  void aNullThatIsCertainToBeDereferencedAfterAMergeIsReported() {
    final String body =
        "  void t(boolean b) {\n    String s = null;\n    if (b) {\n      s = \"x\";\n    }\n    s.length();\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH, body)).containsExactly("7:5");
    assertThat(run(NullDataflowCheck.Mode.GUARANTEED_DEREF, body)).isEmpty();
    final String test =
        "  void t(String s, boolean b) {\n    if (s == null) {\n      b = false;\n    }\n    s.length();\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH, test)).containsExactly("6:5");
    final String two =
        "  void t(String s, boolean b) {\n    if (s == null) {\n      b = false;\n    }\n"
            + "    if (b) {\n      s.length();\n    } else {\n      s.trim();\n    }\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.GUARANTEED_DEREF, two)).containsExactly("7:7");
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH, two)).isEmpty();
  }

  @Test
  void aNullThatSomePathLeavesUnusedOrReturnsFromIsNotReported() {
    final String body =
        "  void t(String s, boolean b) {\n    if (s == null) {\n      return;\n    }\n    s.length();\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH, body)).isEmpty();
    final String branch =
        "  void t(String s, boolean b) {\n    if (s == null) {\n      b = false;\n    }\n    if (b) {\n      s.length();\n    }\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH, branch)).isEmpty();
  }

  @Test
  void aNullReachedOnlyThroughACatchIsAnExceptionPathPattern() {
    final String body =
        "  void t(String s) {\n    try {\n      read();\n    } catch (RuntimeException e) {\n"
            + "      if (s == null) {\n        read();\n      }\n      s.length();\n    }\n  }\n  void read() {}\n";
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH_EXCEPTION, body))
        .containsExactly("9:7");
    assertThat(run(NullDataflowCheck.Mode.NULL_ON_SOME_PATH, body)).isEmpty();
    final String two =
        body.replace(
                "s.length();",
                "if (flag()) {\n        s.trim();\n      } else {\n        s.strip();\n      }")
            + "  boolean flag() { return true; }\n";
    assertThat(run(NullDataflowCheck.Mode.GUARANTEED_DEREF_ON_EXCEPTION_PATH, two))
        .containsExactly("10:9");
    assertThat(run(NullDataflowCheck.Mode.GUARANTEED_DEREF, two)).isEmpty();
  }

  @Test
  void aNullThatIsCertainToBeReturnedOrPassedAfterAMergeIsReportedWithTheMatchingPattern() {
    final String returned =
        "  @NonNull String t(boolean b) {\n    String s = null;\n    if (b) {\n      s = \"x\";\n    }\n    return s;\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.NONNULL_RETURN_VIOLATION, returned))
        .containsExactly("7:12");
    final String passed =
        "  void t(Api api, boolean b) {\n    String s = null;\n    if (b) {\n      s = \"x\";\n    }\n    api.take(s);\n  }\n";
    assertThat(
            run(
                NullDataflowCheck.Mode.NULL_PARAM_DEREF,
                passed,
                "api.take(s)",
                target("Api", "take", 1, "javax.annotation.Nonnull")))
        .containsExactly("7:14");
  }
}
