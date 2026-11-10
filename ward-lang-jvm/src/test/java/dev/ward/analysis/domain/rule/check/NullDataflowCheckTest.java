package dev.ward.analysis.domain.rule.check;

import static dev.ward.analysis.domain.rule.check.NullTestSupport.run;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NullDataflowCheckTest {

  @Test
  void aVariableThatIsNullWhereItIsDereferencedIsReported() {
    final String body = "  void t() {\n    String s = null;\n    s.length();\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.ALWAYS_NULL, body)).containsExactly("4:5");
    assertThat(
            run(
                NullDataflowCheck.Mode.ALWAYS_NULL,
                body.replace("s.length();", "s = \"x\";\n    s.length();")))
        .isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.ALWAYS_NULL,
                body.replace("s.length();", "if (s == null) {\n      s.length();\n    }")))
        .containsExactly("5:7");
    assertThat(
            run(
                NullDataflowCheck.Mode.ALWAYS_NULL,
                body.replace("s.length();", "if (s != null) {\n      s.length();\n    }")))
        .isEmpty();
    assertThat(
            run(NullDataflowCheck.Mode.ALWAYS_NULL, body.replace("String s = null;", "String s;")))
        .isEmpty();
  }

  @Test
  void closeOnANullVariableIsAClosingNullNotAnAlwaysNull() {
    final String body =
        "  void t() throws Exception {\n    java.io.Closeable c = null;\n    c.close();\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.CLOSING_NULL, body)).containsExactly("4:5");
    assertThat(run(NullDataflowCheck.Mode.ALWAYS_NULL, body)).isEmpty();
  }

  @Test
  void aNullOnlyAnExceptionPathHoldsIsAnExceptionPattern() {
    final String body =
        "  void t() {\n    String s = null;\n    try {\n      s = read();\n    } catch (RuntimeException e) {\n"
            + "      s.length();\n    }\n  }\n  String read() { return \"\"; }\n";
    assertThat(run(NullDataflowCheck.Mode.ALWAYS_NULL_EXCEPTION, body)).containsExactly("7:7");
    assertThat(run(NullDataflowCheck.Mode.ALWAYS_NULL, body)).isEmpty();
  }

  @Test
  void aParameterMarkedPossiblyNullIsReportedUntilItIsChecked() {
    final String body = "  void t(@CheckForNull String s) {\n    s.length();\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.ARGUMENT_MIGHT_BE_NULL, body)).containsExactly("3:5");
    assertThat(
            run(
                NullDataflowCheck.Mode.ARGUMENT_MIGHT_BE_NULL,
                body.replace("s.length();", "if (s != null) {\n      s.length();\n    }")))
        .isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.ARGUMENT_MIGHT_BE_NULL,
                body.replace("@CheckForNull", "@Nullable")))
        .isEmpty();
  }

  @Test
  void aNullComparedAfterTheValueWasDereferencedIsReported() {
    final String body =
        "  void t(String s) {\n    s.length();\n    if (s == null) {\n      return;\n    }\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.WOULD_HAVE_BEEN_NPE, body)).containsExactly("4:9");
    assertThat(
            run(
                NullDataflowCheck.Mode.WOULD_HAVE_BEEN_NPE,
                body.replace("s.length();", "s = other();")))
        .isEmpty();
  }

  @Test
  void aTryThatCatchesNullPointerExceptionAndUnknownFactsAreSilent() {
    final String body =
        "  void t() {\n    String s = null;\n    try {\n      s.length();\n    } catch (NullPointerException e) {\n      return;\n    }\n  }\n";
    assertThat(run(NullDataflowCheck.Mode.ALWAYS_NULL, body)).isEmpty();
    assertThat(
            run(
                NullDataflowCheck.Mode.ALWAYS_NULL,
                "  void t(java.util.function.Supplier<String> g) {\n    String s = null;\n    Runnable r = () -> g.get();\n"
                    + "    s = g.get();\n    s.length();\n  }\n"))
        .isEmpty();
    assertThat(
            new ParsedFacts("class C {}")
                .plain(new NullDataflowCheck(NullDataflowCheck.Mode.ALWAYS_NULL, "m")))
        .isEmpty();
  }
}
